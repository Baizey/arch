package org.baizey.harness

import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.PolicyCollection

class HarnessContext(
    val interactionPort: HarnessInteractionPort,
    val policies: PolicyCollection
) {
    val pathPolicyLogic: PathPolicyLogic = policies.path
    val gitPolicyLogic: GitPolicyLogic = policies.git

    fun reloadFromPersistence() {
        pathPolicyLogic.reloadFromPersistence()
        gitPolicyLogic.reloadFromPersistence()
    }
}
