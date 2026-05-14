#!/usr/bin/env python3
import base64
import errno
import json
import os
import shutil
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

import pyfuse3
import trio
from pyfuse3 import EntryAttributes, FileInfo, FUSEError, ReaddirToken, RequestContext, SetattrFields
from sandbox_policy import evaluate_policy as evaluate_policy_against_snapshot

WORKSPACE_PATH = Path(os.environ.get("ARCH_WORKSPACE_ROOT", "/arch/workspace"))
BACKING_PATH = Path(os.environ.get("ARCH_BACKING_ROOT", "/arch/backing"))
LISTEN_HOST = os.environ.get("ARCH_SANDBOX_LISTEN_HOST", "0.0.0.0")
LISTEN_PORT = int(os.environ.get("ARCH_SANDBOX_PORT", "18080"))

ACTIVE_POLICY = {
    "policies": [],
    "deniedPathPrefixes": [],
}
POLICY_LOCK = threading.RLock()
BLOCKED_OPERATIONS = []
BLOCKED_LOCK = threading.RLock()
FUSE_STATE = {
    "mode": "starting",
    "error": None,
}


def workspace_root() -> Path:
    return WORKSPACE_PATH


def backing_root() -> Path:
    return BACKING_PATH


def normalize_path(raw_path) -> str:
    return os.path.normpath(str(raw_path))


def is_write_flags(flags: int) -> bool:
    return bool(flags & os.O_WRONLY or flags & os.O_RDWR or flags & os.O_APPEND or flags & os.O_CREAT or flags & os.O_TRUNC)


def record_blocked_operation(path: str, action: str, reason: str):
    with BLOCKED_LOCK:
        BLOCKED_OPERATIONS.append({"path": path, "action": action, "reason": reason})


def drain_blocked_operations():
    with BLOCKED_LOCK:
        items = list(BLOCKED_OPERATIONS)
        BLOCKED_OPERATIONS.clear()
        unique_items = []
        seen = set()
        for item in items:
            rendered = f"{item['action']} {item['path']} ({item['reason']})"
            if rendered in seen:
                continue
            seen.add(rendered)
            unique_items.append(rendered)
        return unique_items


def append_blocked_summary(stderr: str, blocked_operations):
    if not blocked_operations:
        return stderr
    summary_lines = ["Sandbox policy blocked filesystem access:"] + [f"- {item}" for item in blocked_operations]
    summary = "\n".join(summary_lines)
    if not stderr:
        return summary
    if summary in stderr:
        return stderr
    return f"{stderr.rstrip()}\n{summary}\n"


def evaluate_policy(path: str, action: str):
    with POLICY_LOCK:
        snapshot = {
            "policies": list(ACTIVE_POLICY["policies"]),
            "deniedPathPrefixes": list(ACTIVE_POLICY["deniedPathPrefixes"]),
        }
    return evaluate_policy_against_snapshot(path, action, snapshot)


def ensure_allowed_path(path: Path, action: str) -> None:
    normalized = normalize_path(path)
    is_allowed, reason = evaluate_policy(normalized, action)
    if not is_allowed:
        record_blocked_operation(normalized, action, reason)
        raise FUSEError(errno.EACCES)


def resolve_workspace_path(raw_cwd):
    root = workspace_root()
    if not raw_cwd:
        return root
    candidate = Path(raw_cwd)
    if not candidate.is_absolute():
        candidate = root / candidate
    normalized = Path(os.path.normpath(str(candidate)))
    if normalized != root and root not in normalized.parents:
        raise ValueError(f"cwd escapes workspace root: {normalized}")
    return normalized


class PassthroughOperations(pyfuse3.Operations):
    enable_writeback_cache = False

    def __init__(self, source_root: Path) -> None:
        super().__init__()
        self.source_root = source_root.resolve()
        self.inode_path_map = {pyfuse3.ROOT_INODE: self.source_root}
        self.path_inode_map = {str(self.source_root): pyfuse3.ROOT_INODE}
        self.open_handles = {}
        self.next_inode = pyfuse3.ROOT_INODE + 1

    def _path_for_inode(self, inode):
        path = self.inode_path_map.get(inode)
        if path is None:
            raise FUSEError(errno.ENOENT)
        return path

    def _inode_for_path(self, path: Path):
        normalized = str(path.resolve())
        inode = self.path_inode_map.get(normalized)
        if inode is not None:
            return inode
        inode = self.next_inode
        self.next_inode += 1
        self.path_inode_map[normalized] = inode
        self.inode_path_map[inode] = Path(normalized)
        return inode

    def _entry_for_path(self, path: Path):
        ensure_allowed_path(path, "metadata")
        st = os.lstat(path)
        entry = EntryAttributes()
        entry.st_ino = self._inode_for_path(path)
        entry.generation = 0
        entry.entry_timeout = 1
        entry.attr_timeout = 1
        entry.st_mode = st.st_mode
        entry.st_nlink = st.st_nlink
        entry.st_uid = st.st_uid
        entry.st_gid = st.st_gid
        entry.st_rdev = st.st_rdev
        entry.st_size = st.st_size
        entry.st_atime_ns = st.st_atime_ns
        entry.st_mtime_ns = st.st_mtime_ns
        entry.st_ctime_ns = st.st_ctime_ns
        entry.st_blksize = 4096
        entry.st_blocks = getattr(st, "st_blocks", max(1, (st.st_size + 511) // 512))
        return entry

    async def getattr(self, inode, ctx=None):
        return self._entry_for_path(self._path_for_inode(inode))

    async def lookup(self, parent_inode, name, ctx):
        parent = self._path_for_inode(parent_inode)
        target = (parent / os.fsdecode(name)).resolve()
        if not target.exists():
            raise FUSEError(errno.ENOENT)
        return self._entry_for_path(target)

    async def opendir(self, inode, ctx):
        path = self._path_for_inode(inode)
        ensure_allowed_path(path, "read")
        if not path.is_dir():
            raise FUSEError(errno.ENOTDIR)
        return inode

    async def readdir(self, fh, start_id, token):
        directory = self._path_for_inode(fh)
        ensure_allowed_path(directory, "read")
        entries = sorted(os.scandir(directory), key=lambda entry: entry.name)
        for index, entry in enumerate(entries[start_id:], start=start_id + 1):
            entry_path = Path(entry.path).resolve()
            is_allowed, _ = evaluate_policy(str(entry_path), "metadata")
            if not is_allowed:
                continue
            if not pyfuse3.readdir_reply(token, os.fsencode(entry.name), self._entry_for_path(entry_path), index):
                break

    async def open(self, inode, flags, ctx):
        path = self._path_for_inode(inode)
        ensure_allowed_path(path, "edit" if is_write_flags(flags) else "read")
        try:
            fd = os.open(path, flags)
        except OSError as exc:
            raise FUSEError(exc.errno)
        self.open_handles[fd] = path
        return FileInfo(fh=fd)

    async def create(self, parent_inode, name, mode, flags, ctx):
        parent = self._path_for_inode(parent_inode)
        ensure_allowed_path(parent, "create")
        path = (parent / os.fsdecode(name)).resolve()
        ensure_allowed_path(path, "create")
        try:
            fd = os.open(path, flags | os.O_CREAT, mode)
        except OSError as exc:
            raise FUSEError(exc.errno)
        self.open_handles[fd] = path
        return FileInfo(fh=fd), self._entry_for_path(path)

    async def read(self, fh, off, size):
        path = self.open_handles.get(fh)
        if path is not None:
            ensure_allowed_path(path, "read")
        return os.pread(fh, size, off)

    async def write(self, fh, off, buf):
        path = self.open_handles.get(fh)
        if path is not None:
            ensure_allowed_path(path, "edit")
        return os.pwrite(fh, buf, off)

    async def release(self, fh):
        self.open_handles.pop(fh, None)
        os.close(fh)

    async def mkdir(self, parent_inode, name, mode, ctx):
        parent = self._path_for_inode(parent_inode)
        ensure_allowed_path(parent, "create")
        path = (parent / os.fsdecode(name)).resolve()
        ensure_allowed_path(path, "create")
        try:
            os.mkdir(path, mode)
        except OSError as exc:
            raise FUSEError(exc.errno)
        return self._entry_for_path(path)

    async def unlink(self, parent_inode, name, ctx):
        parent = self._path_for_inode(parent_inode)
        path = (parent / os.fsdecode(name)).resolve()
        ensure_allowed_path(path, "delete")
        try:
            os.unlink(path)
        except OSError as exc:
            raise FUSEError(exc.errno)

    async def rmdir(self, parent_inode, name, ctx):
        parent = self._path_for_inode(parent_inode)
        path = (parent / os.fsdecode(name)).resolve()
        ensure_allowed_path(path, "delete")
        try:
            os.rmdir(path)
        except OSError as exc:
            raise FUSEError(exc.errno)

    async def rename(self, parent_inode_old, name_old, parent_inode_new, name_new, flags, ctx):
        if flags != 0:
            raise FUSEError(errno.EINVAL)
        old_parent = self._path_for_inode(parent_inode_old)
        new_parent = self._path_for_inode(parent_inode_new)
        old_path = (old_parent / os.fsdecode(name_old)).resolve()
        new_path = (new_parent / os.fsdecode(name_new)).resolve()
        ensure_allowed_path(old_path, "delete")
        ensure_allowed_path(new_path, "create")
        try:
            os.rename(old_path, new_path)
        except OSError as exc:
            raise FUSEError(exc.errno)

    async def setattr(self, inode, attr, fields: SetattrFields, fh, ctx):
        path = self._path_for_inode(inode)
        ensure_allowed_path(path, "edit")
        try:
            if fields.update_size:
                os.truncate(path, attr.st_size)
            if fields.update_mode:
                os.chmod(path, attr.st_mode)
            if fields.update_uid or fields.update_gid:
                uid = attr.st_uid if fields.update_uid else -1
                gid = attr.st_gid if fields.update_gid else -1
                os.chown(path, uid, gid)
        except OSError as exc:
            raise FUSEError(exc.errno)
        return self._entry_for_path(path)

    async def access(self, inode, mode, ctx):
        path = self._path_for_inode(inode)
        action = "edit" if mode & os.W_OK else "read"
        ensure_allowed_path(path, action)
        return True

    async def readlink(self, inode, ctx):
        path = self._path_for_inode(inode)
        ensure_allowed_path(path, "read")
        try:
            return os.fsencode(os.readlink(path))
        except OSError as exc:
            raise FUSEError(exc.errno)

    async def symlink(self, parent_inode, name, target, ctx):
        parent = self._path_for_inode(parent_inode)
        link_path = (parent / os.fsdecode(name)).resolve()
        ensure_allowed_path(link_path, "create")
        try:
            os.symlink(os.fsdecode(target), link_path)
        except OSError as exc:
            raise FUSEError(exc.errno)
        return self._entry_for_path(link_path)

    async def statfs(self, ctx):
        stats = os.statvfs(self.source_root)
        result = pyfuse3.StatvfsData()
        result.f_bsize = stats.f_bsize
        result.f_frsize = stats.f_frsize
        result.f_blocks = stats.f_blocks
        result.f_bfree = stats.f_bfree
        result.f_bavail = stats.f_bavail
        result.f_files = stats.f_files
        result.f_ffree = stats.f_ffree
        result.f_favail = stats.f_favail
        return result


def start_fuse_passthrough():
    if WORKSPACE_PATH.exists() and not WORKSPACE_PATH.is_symlink():
        shutil.rmtree(WORKSPACE_PATH)
    WORKSPACE_PATH.mkdir(parents=True, exist_ok=True)

    operations = PassthroughOperations(backing_root())
    fuse_options = set(pyfuse3.default_options)
    fuse_options.add("fsname=arch-sandbox")

    def run_loop():
        pyfuse3.init(operations, str(WORKSPACE_PATH), fuse_options)
        try:
            trio.run(pyfuse3.main)
        finally:
            pyfuse3.close()

    thread = threading.Thread(target=run_loop, name="arch-fuse", daemon=True)
    thread.start()
    return thread


def is_workspace_mounted() -> bool:
    mount_target = os.path.normpath(str(WORKSPACE_PATH))
    with open("/proc/self/mountinfo", "r", encoding="utf-8") as handle:
        for line in handle:
            parts = line.split()
            if len(parts) > 4 and parts[4] == mount_target:
                return True
    return False


def boot_filesystem():
    try:
        start_fuse_passthrough()
        for _ in range(50):
            if is_workspace_mounted():
                FUSE_STATE["mode"] = "fuse-passthrough"
                return
            time.sleep(0.2)
        raise RuntimeError("FUSE mount did not become active.")
    except Exception as exc:
        FUSE_STATE["mode"] = "degraded"
        FUSE_STATE["error"] = str(exc)
        if os.path.lexists(WORKSPACE_PATH):
            shutil.rmtree(WORKSPACE_PATH, ignore_errors=True)
        os.symlink(BACKING_PATH, WORKSPACE_PATH, target_is_directory=True)


class Handler(BaseHTTPRequestHandler):
    server_version = "arch-sandbox/0.2"

    def log_message(self, format, *args):
        return

    def send_json(self, status_code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_exec_result(self, exit_code, stdout="", stderr="", blocked_operations=None):
        blocked_operations = blocked_operations or []
        self.send_json(
            200,
            {
                "exitCode": exit_code,
                "stdout": stdout,
                "stderr": append_blocked_summary(stderr, blocked_operations),
                "blockedOperations": blocked_operations,
                "policySummary": {
                    "policyCount": len(ACTIVE_POLICY["policies"]),
                    "deniedPrefixCount": len(ACTIVE_POLICY["deniedPathPrefixes"]),
                },
                "mode": FUSE_STATE["mode"],
            },
        )

    def do_GET(self):
        if self.path == "/health":
            self.send_json(
                200,
                {
                    "ok": True,
                    "workspacePath": str(WORKSPACE_PATH),
                    "workspaceRoot": str(workspace_root()),
                    "backingPath": str(BACKING_PATH),
                    "backingRoot": str(backing_root()),
                    "mode": FUSE_STATE["mode"],
                    "error": FUSE_STATE["error"],
                },
            )
            return
        self.send_json(404, {"ok": False, "error": "not_found"})

    def do_POST(self):
        if self.path != "/exec":
            self.send_json(404, {"ok": False, "error": "not_found"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
            policy_snapshot = payload.get("policySnapshot") or ACTIVE_POLICY
            with POLICY_LOCK:
                ACTIVE_POLICY["policies"] = policy_snapshot.get("policies", [])
                ACTIVE_POLICY["deniedPathPrefixes"] = policy_snapshot.get("deniedPathPrefixes", [])
            drain_blocked_operations()

            cwd = resolve_workspace_path(payload.get("cwd"))
            timeout_seconds = int(payload.get("timeoutSeconds", 30))
            env = os.environ.copy()
            env.update({str(k): str(v) for k, v in (payload.get("env") or {}).items()})
            policy_json = json.dumps(ACTIVE_POLICY)
            env["ARCH_PATH_POLICY_SNAPSHOT_JSON"] = policy_json
            env["ARCH_PATH_POLICY_SNAPSHOT_BASE64"] = base64.b64encode(policy_json.encode("utf-8")).decode("ascii")
            env["ARCH_WORKSPACE_ROOT"] = str(WORKSPACE_PATH)
            env["ARCH_BACKING_ROOT"] = str(BACKING_PATH)

            try:
                completed = subprocess.run(
                    ["/usr/bin/bash", "--noprofile", "--norc", "-lc", payload["command"]],
                    cwd=str(cwd),
                    env=env,
                    capture_output=True,
                    text=True,
                    timeout=timeout_seconds,
                )
            except PermissionError as exc:
                blocked_operations = drain_blocked_operations()
                self.send_exec_result(
                    exit_code=126,
                    stderr=f"Sandbox denied access while starting command in {cwd}: {exc}",
                    blocked_operations=blocked_operations,
                )
                return
            blocked_operations = drain_blocked_operations()
            self.send_exec_result(
                exit_code=completed.returncode,
                stdout=completed.stdout,
                stderr=completed.stderr,
                blocked_operations=blocked_operations,
            )
        except subprocess.TimeoutExpired as exc:
            blocked_operations = drain_blocked_operations()
            self.send_exec_result(
                exit_code=124,
                stdout=exc.stdout or "",
                stderr=(exc.stderr or "") + "\nCommand timed out.",
                blocked_operations=blocked_operations,
            )
        except PermissionError as exc:
            blocked_operations = drain_blocked_operations()
            self.send_exec_result(
                exit_code=126,
                stderr=f"Sandbox denied filesystem access: {exc}",
                blocked_operations=blocked_operations,
            )
        except Exception as exc:
            blocked_operations = drain_blocked_operations()
            self.send_exec_result(
                exit_code=125,
                stderr=f"Sandbox daemon error: {exc}",
                blocked_operations=blocked_operations,
            )


if __name__ == "__main__":
    boot_filesystem()
    ThreadingHTTPServer((LISTEN_HOST, LISTEN_PORT), Handler).serve_forever()
