package org.baizey.harness.policy

import org.baizey.harness.policy.git.GitAccessType
import org.baizey.harness.policy.git.GitPolicyResult
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult

interface PathPolicyLogic {
    fun inspectPath(rawFilePath: String): PathAccessInspection
    fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult
    fun activePathPolicies(): List<PathPolicy>
    fun renderAgentPolicySummary(): String
    fun reloadFromPersistence()
}

interface GitPolicyLogic {
    fun evaluate(rawGitRootPath: String, accessType: GitAccessType): GitPolicyResult
    fun renderAgentPolicySummary(): String
    fun reloadFromPersistence()
}

data class PolicyCollection(
    val git: GitPolicyLogic,
    val path: PathPolicyLogic
)
