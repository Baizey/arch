package org.baizey.harness

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.GitPolicyLogic
import org.baizey.harness.policy.ShellPolicyLogic

class HarnessContext(
    val interactionPort: HarnessInteractionPort
) {
    val pathPolicyLogic: PathPolicyLogic = PathPolicyLogic(interactionPort)
    val shellPolicyLogic: ShellPolicyLogic = ShellPolicyLogic(interactionPort)
    val gitPolicyLogic: GitPolicyLogic = GitPolicyLogic(interactionPort)


    fun reloadFromPersistence() {
        pathPolicyLogic.reloadFromPersistence()
        shellPolicyLogic.reloadFromPersistence()
        gitPolicyLogic.reloadFromPersistence()
    }
}
