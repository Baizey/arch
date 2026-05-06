package org.baizey.harness.policy

import org.baizey.harness.policy.git.GitAccessType
import org.baizey.harness.policy.git.GitPolicyResult
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicyResult

interface PathPolicy {
    fun inspectPath(rawFilePath: String): PathAccessInspection
    fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult
    fun renderAgentPolicySummary(): String
}

interface GitPolicy {
    fun evaluate(rawGitRootPath: String, accessType: GitAccessType): GitPolicyResult
    fun renderAgentPolicySummary(): String
}

class PolicyCollection(
    val gitPolicy: GitPolicy,
    val pathPolicy: PathPolicy
)