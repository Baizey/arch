package org.baizey.harness.policy

import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.shared.PolicyLifetime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PathPolicyLogicTest {
    private var permissionDecisionProvider: (PermissionRequest) -> PermissionDecision = { request ->
        PermissionDecision(
            isAllowed = true,
            lifetime = PolicyLifetime.ONCE,
            scope = request.path
        )
    }

    private val interactionPort = object : HarnessInteractionPort {
        val requests = mutableListOf<PermissionRequest>()

        override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
            return AskUserAnswer(
                isAccepted = true,
                selection = options.firstOrNull().orEmpty(),
                selectionIndex = 0
            )
        }

        override fun requestPermission(request: PermissionRequest): PermissionDecision {
            requests += request
            return permissionDecisionProvider(request)
        }
    }

    private val tool = PathPolicyLogic(interactionPort)

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `requests the full ancestor ladder for fs scopes`() {
        val file = Files.createDirectories(tempDir.resolve("nested/inner")).resolve("example.txt")
        Files.writeString(file, "hello")

        tool.evaluate(file.toString(), FsAccessType.READ)

        assertEquals(1, interactionPort.requests.size)
        assertEquals(file.toAbsolutePath().normalize().toString(), interactionPort.requests.single().path)
        assertTrue(
            interactionPort.requests.single().scopeOptions.size >= 3,
            interactionPort.requests.single().scopeOptions.toString()
        )

        val expectedScopes = buildExpectedScopes(file.toAbsolutePath().normalize())
        assertEquals(expectedScopes, interactionPort.requests.single().scopeOptions)
    }

    private fun buildExpectedScopes(path: Path): List<String> {
        val scopes = mutableListOf<String>()
        var current: Path? = path
        while (current != null) {
            scopes += current.normalize().toString()
            current = current.parent
        }
        return scopes
    }
}
