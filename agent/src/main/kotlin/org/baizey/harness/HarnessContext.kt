package org.baizey.harness

import org.baizey.harness.policy.UserPathPolicyLogic
import org.baizey.harness.policy.UserGitPolicyLogic

class HarnessContext(
    val interactionPort: HarnessInteractionPort
) {
    val userPathPolicyLogic: UserPathPolicyLogic = UserPathPolicyLogic(interactionPort)
    val gitPolicyLogic: UserGitPolicyLogic = UserGitPolicyLogic(interactionPort)


    fun reloadFromPersistence() {
        userPathPolicyLogic.reloadFromPersistence()
        gitPolicyLogic.reloadFromPersistence()
    }
}
