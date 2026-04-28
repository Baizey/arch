package org.baizey.harness.policy.path

data class PathAccessInspection(
    val path: String,
    val decisions: List<PathAccessDecisionEntry>
)

data class PathAccessDecisionEntry(
    val accessType: FsAccessType,
    val decision: PathAccessDecision,
    val pattern: String?,
    val reason: String
)

enum class PathAccessDecision {
    ALLOW,
    DENY,
    ASK_PERMISSION
}
