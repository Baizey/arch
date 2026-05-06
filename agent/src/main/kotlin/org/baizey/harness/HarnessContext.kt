package org.baizey.harness

import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.PolicyCollection

class HarnessContext(
    val interactionPort: HarnessInteractionPort,
    val policies: PolicyCollection
) {
    val userPathPolicyLogic: PathPolicyLogic = policies.path
    val gitPolicyLogic: GitPolicyLogic = policies.git


    fun reloadFromPersistence() {
        userPathPolicyLogic.reloadFromPersistence()
        gitPolicyLogic.reloadFromPersistence()
    }
}
