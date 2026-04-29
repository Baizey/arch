package org.baizey.harness

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.GitPolicyLogic

class HarnessContext(
    val interactionPort: HarnessInteractionPort
) {
    val pathPolicyLogic: PathPolicyLogic = PathPolicyLogic(interactionPort)
    val gitPolicyLogic: GitPolicyLogic = GitPolicyLogic(interactionPort)


    fun reloadFromPersistence() {
        pathPolicyLogic.reloadFromPersistence()
        gitPolicyLogic.reloadFromPersistence()
    }
}
