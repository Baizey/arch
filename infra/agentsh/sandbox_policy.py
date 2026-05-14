import os
from pathlib import Path


def normalize_policy_path(raw_path: str) -> str:
    return os.path.normpath(str(raw_path))


def action_to_access_types(action: str):
    if action == "read":
        return ["READ"]
    if action == "metadata":
        return ["READ", "WRITE", "EDIT", "DELETE", "EXECUTE"]
    if action == "create":
        return ["WRITE"]
    if action == "edit":
        return ["EDIT", "WRITE"]
    if action == "delete":
        return ["DELETE"]
    if action == "execute":
        return ["EXECUTE", "READ"]
    return ["READ"]


def is_path_within_policy_scope(path: str, pattern: str) -> bool:
    target = Path(path)
    scope = Path(pattern)
    return target == scope or scope in target.parents


def evaluate_policy(path: str, action: str, active_policy: dict):
    access_types = action_to_access_types(action)
    denied_prefixes = list(active_policy.get("deniedPathPrefixes", []))
    policies = list(active_policy.get("policies", []))

    for prefix in denied_prefixes:
        normalized_prefix = normalize_policy_path(prefix)
        if is_path_within_policy_scope(path, normalized_prefix):
            return False, f"denied-prefix:{normalized_prefix}"

    candidates = []
    for policy in policies:
        try:
            pattern = normalize_policy_path(policy["pattern"])
        except Exception:
            continue
        policy_access_types = set(policy.get("accessTypes", []))
        if not any(access_type in policy_access_types for access_type in access_types):
            continue
        if is_path_within_policy_scope(path, pattern):
            candidates.append((len(pattern), policy, pattern))

    if not candidates:
        if action == "metadata":
            return True, "traversal:default"
        if action == "read":
            normalized_path = normalize_policy_path(path)
            for policy in policies:
                if not policy.get("isAllowed", False):
                    continue
                try:
                    pattern = normalize_policy_path(policy["pattern"])
                except Exception:
                    continue
                if is_path_within_policy_scope(pattern, normalized_path):
                    return True, f"traversal:{pattern}"
        return False, "unknown"

    _, policy, pattern = max(candidates, key=lambda item: item[0])
    if policy.get("isAllowed", False):
        return True, f"allowed:{pattern}"
    return False, f"denied:{pattern}"
