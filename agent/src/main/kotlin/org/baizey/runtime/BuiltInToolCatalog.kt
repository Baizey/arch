package org.baizey.runtime

data class BuiltInToolGroup(
    val id: String,
    val label: String,
    val description: String
)

data class BuiltInToolSubgroup(
    val id: String,
    val groupId: String,
    val label: String,
    val description: String
)

data class BuiltInToolDescriptor(
    val id: String,
    val toolName: String,
    val groupId: String,
    val subgroupId: String?,
    val label: String,
    val description: String
)

object BuiltInToolCatalog {
    val groups = listOf(
        BuiltInToolGroup("fs", "Filesystem", "File inspection and modification tools."),
        BuiltInToolGroup("sandbox", "Sandbox", "Container-backed execution tools."),
        BuiltInToolGroup("web", "Web", "Web search and website fetching tools."),
        BuiltInToolGroup("git", "Git", "Repository inspection and mutation tools."),
        BuiltInToolGroup("ask-user", "Ask User", "Question routing back to the user.")
    )

    val subgroups = listOf(
        BuiltInToolSubgroup("fs-inspect", "fs", "Inspect", "Inspect policy and access status for a path."),
        BuiltInToolSubgroup("fs-read", "fs", "Read", "Read directory, file, or text search results."),
        BuiltInToolSubgroup("fs-create", "fs", "Create", "Create new files."),
        BuiltInToolSubgroup("fs-update", "fs", "Update", "Edit existing files in place."),
        BuiltInToolSubgroup("fs-move", "fs", "Move / Copy", "Move or copy filesystem paths."),
        BuiltInToolSubgroup("fs-delete", "fs", "Delete", "Delete filesystem paths."),
        BuiltInToolSubgroup("sandbox-shell", "sandbox", "Shell", "Execute commands inside the sandbox container."),
        BuiltInToolSubgroup("web-search", "web", "Search", "Discover candidate web pages."),
        BuiltInToolSubgroup("web-fetch", "web", "Fetch", "Read one chosen website."),
        BuiltInToolSubgroup("git-read", "git", "Read", "Inspect local repository state and history."),
        BuiltInToolSubgroup("git-modify", "git", "Modify", "Change local repository state."),
        BuiltInToolSubgroup("git-pull", "git", "Pull / Fetch", "Bring remote data into the local repository."),
        BuiltInToolSubgroup("git-push", "git", "Push", "Push local commits to a remote.")
    )

    val tools = listOf(
        BuiltInToolDescriptor("ask_user", "ask_user", "ask-user", null, "Ask User", "Ask the user a multiple-choice question."),
        BuiltInToolDescriptor("inspect_path_access", "inspect_path_access", "fs", "fs-inspect", "Inspect Path Access", "Inspect current filesystem access for one path."),
        BuiltInToolDescriptor("ask_path_permission", "ask_path_permission", "fs", "fs-inspect", "Ask Path Permission", "Ask the user to allow or deny filesystem access for one path."),
        BuiltInToolDescriptor("list_directory", "list_directory", "fs", "fs-read", "List Directory", "List files and folders in a directory."),
        BuiltInToolDescriptor("search_files", "search_files", "fs", "fs-read", "Search Files", "Search file names or file contents."),
        BuiltInToolDescriptor("read_file", "read_file", "fs", "fs-read", "Read File", "Read a line range from a file."),
        BuiltInToolDescriptor("edit_file", "edit_file", "fs", "fs-update", "Edit File", "Replace a precise line range in a file."),
        BuiltInToolDescriptor("write_file", "write_file", "fs", "fs-create", "Write File", "Create or overwrite a file."),
        BuiltInToolDescriptor("move_or_copy_path", "move_or_copy_path", "fs", "fs-move", "Move or Copy Path", "Move or copy a file or directory."),
        BuiltInToolDescriptor("delete_path", "delete_path", "fs", "fs-delete", "Delete Path", "Delete a file or directory."),
        BuiltInToolDescriptor("shell", "shell", "sandbox", "sandbox-shell", "Shell", "Execute a shell command inside the sandbox container."),
        BuiltInToolDescriptor("execute_code", "execute_code", "sandbox", "sandbox-shell", "Execute Code", "Write code to a temporary script file, run it in the sandbox, then delete it."),
        BuiltInToolDescriptor("search_web", "search_web", "web", "web-search", "Search Web", "Search the public web for candidate pages."),
        BuiltInToolDescriptor("fetch_website", "fetch_website", "web", "web-fetch", "Fetch Website", "Fetch and read website content."),
        BuiltInToolDescriptor("git_status", "git_status", "git", "git-read", "Git Status", "Inspect repository status."),
        BuiltInToolDescriptor("git_diff", "git_diff", "git", "git-read", "Git Diff", "Inspect repository diffs."),
        BuiltInToolDescriptor("git_log", "git_log", "git", "git-read", "Git Log", "Inspect repository history."),
        BuiltInToolDescriptor("git_add", "git_add", "git", "git-modify", "Git Add", "Stage repository changes."),
        BuiltInToolDescriptor("git_commit", "git_commit", "git", "git-modify", "Git Commit", "Create a commit."),
        BuiltInToolDescriptor("git_checkout", "git_checkout", "git", "git-modify", "Git Checkout", "Switch or restore repository state."),
        BuiltInToolDescriptor("git_fetch", "git_fetch", "git", "git-pull", "Git Fetch", "Fetch remote updates."),
        BuiltInToolDescriptor("git_pull", "git_pull", "git", "git-pull", "Git Pull", "Fetch and merge remote updates."),
        BuiltInToolDescriptor("git_push", "git_push", "git", "git-push", "Git Push", "Push local commits to a remote.")
    )

    private val toolsByName = tools.associateBy { it.toolName }

    fun snapshot(): ToolFilterCatalogSnapshot {
        return ToolFilterCatalogSnapshot(
            groups = groups.map { ToolFilterGroupSnapshot(it.id, it.label, it.description) },
            subgroups = subgroups.map { ToolFilterSubgroupSnapshot(it.id, it.groupId, it.label, it.description) },
            tools = tools.map { ToolFilterToolSnapshot(it.id, it.toolName, it.groupId, it.subgroupId, it.label, it.description) }
        )
    }

    fun allRuleTargetIds(): Set<String> {
        return tools.mapTo(linkedSetOf()) { "tool:${it.id}" }
    }

    fun toolRuleTargetIdsForGroup(groupId: String): List<String> {
        return tools
            .filter { it.groupId == groupId }
            .map { "tool:${it.id}" }
    }

    fun toolRuleTargetIdsForSubgroup(subgroupId: String): List<String> {
        return tools
            .filter { it.subgroupId == subgroupId }
            .map { "tool:${it.id}" }
    }

    fun isEnabled(toolName: String, profile: ToolFilterProfile?): Boolean {
        val descriptor = toolsByName[toolName] ?: return true
        val rules = profile?.rules ?: emptyMap()
        return rules["tool:${descriptor.id}"] != ToolFilterMode.DISABLED
    }
}
