package org.baizey.runtime.sandbox

import org.baizey.harness.policy.PathPolicyLogic
import org.baizey.harness.policy.path.FsAccessType
import org.baizey.harness.policy.path.PathAccessInspection
import org.baizey.harness.policy.path.PathPolicy
import org.baizey.harness.policy.path.PathPolicyResult
import org.baizey.harness.policy.path.PathPolicySnapshot
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.runtime.SandboxConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.ThreadLocalRandom
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DockerSandboxServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `write access is enforced by sandbox`() {
        assertAllowedScenario(
            scenarioName = "write-allowed",
            accessType = FsAccessType.WRITE,
            relativeDir = "allowed",
            command = """printf 'allowed' > allowed/allowed.txt""",
            assertSuccess = { workspace, _ ->
                val targetFile = workspace.resolve("allowed/allowed.txt")
                assertTrue(targetFile.exists(), "Allowed write should create $targetFile")
                assertEquals("allowed", targetFile.readText())
            }
        )

        assertBlockedScenario(
            scenarioName = "write-denied",
            accessType = FsAccessType.WRITE,
            relativeDir = "denied",
            command = """printf 'denied' > denied/denied.txt""",
            isAllowed = false,
            expectedReason = { deniedDirText -> "denied:$deniedDirText" },
            assertFailure = { workspace, _ ->
                val targetFile = workspace.resolve("denied/denied.txt")
                assertTrue(targetFile.notExists(), "Denied write should not create $targetFile")
            }
        )

        assertBlockedScenario(
            scenarioName = "write-unknown",
            accessType = null,
            relativeDir = "unknown",
            command = """printf 'unknown' > unknown/unknown.txt""",
            isAllowed = false,
            expectedReason = { "unknown" },
            assertFailure = { workspace, _ ->
                val targetFile = workspace.resolve("unknown/unknown.txt")
                assertFalse(targetFile.exists(), "Unknown write should not create $targetFile")
            }
        )
    }

    @Test
    fun `read access is enforced by sandbox`() {
        assertAllowedScenario(
            scenarioName = "read-allowed",
            accessType = FsAccessType.READ,
            relativeDir = "readable",
            setup = { workspace ->
                workspace.resolve("readable/message.txt").writeText("hello-read")
            },
            command = """cat readable/message.txt""",
            assertSuccess = { _, result ->
                assertEquals("hello-read", result.stdout.trim())
            }
        )

        assertBlockedScenario(
            scenarioName = "read-denied",
            accessType = FsAccessType.READ,
            relativeDir = "blocked",
            setup = { workspace ->
                workspace.resolve("blocked/message.txt").writeText("secret")
            },
            command = """cat blocked/message.txt""",
            isAllowed = false,
            expectedReason = { deniedDirText -> "denied:$deniedDirText" }
        )

        assertBlockedScenario(
            scenarioName = "read-unknown",
            accessType = null,
            relativeDir = "unknown",
            setup = { workspace ->
                workspace.resolve("unknown/message.txt").writeText("secret")
            },
            command = """cat unknown/message.txt""",
            isAllowed = false,
            expectedReason = { "unknown" }
        )
    }

    @Test
    fun `edit access is enforced by sandbox`() {
        assertAllowedScenario(
            scenarioName = "edit-allowed",
            accessType = FsAccessType.EDIT,
            relativeDir = "editable",
            setup = { workspace ->
                workspace.resolve("editable/message.txt").writeText("hello")
            },
            command = """truncate -s 2 editable/message.txt""",
            assertSuccess = { workspace, _ ->
                assertEquals("he", workspace.resolve("editable/message.txt").readText())
            }
        )

        assertBlockedScenario(
            scenarioName = "edit-denied",
            accessType = FsAccessType.EDIT,
            relativeDir = "blocked",
            setup = { workspace ->
                workspace.resolve("blocked/message.txt").writeText("secret")
            },
            command = """truncate -s 2 blocked/message.txt""",
            isAllowed = false,
            expectedReason = { deniedDirText -> "denied:$deniedDirText" }
        )

        assertBlockedScenario(
            scenarioName = "edit-unknown",
            accessType = null,
            relativeDir = "unknown",
            setup = { workspace ->
                workspace.resolve("unknown/message.txt").writeText("secret")
            },
            command = """truncate -s 2 unknown/message.txt""",
            isAllowed = false,
            expectedReason = { "unknown" }
        )
    }

    @Test
    fun `delete access is enforced by sandbox`() {
        assertAllowedScenario(
            scenarioName = "delete-allowed",
            accessType = FsAccessType.DELETE,
            relativeDir = "trash",
            setup = { workspace ->
                workspace.resolve("trash/message.txt").writeText("delete-me")
            },
            command = """rm trash/message.txt""",
            assertSuccess = { workspace, _ ->
                assertFalse(workspace.resolve("trash/message.txt").exists())
            }
        )

        assertBlockedScenario(
            scenarioName = "delete-denied",
            accessType = FsAccessType.DELETE,
            relativeDir = "blocked",
            setup = { workspace ->
                workspace.resolve("blocked/message.txt").writeText("secret")
            },
            command = """rm blocked/message.txt""",
            isAllowed = false,
            expectedReason = { deniedDirText -> "denied:$deniedDirText" },
            assertFailure = { workspace, _ ->
                assertTrue(workspace.resolve("blocked/message.txt").exists())
            }
        )

        assertBlockedScenario(
            scenarioName = "delete-unknown",
            accessType = null,
            relativeDir = "unknown",
            setup = { workspace ->
                workspace.resolve("unknown/message.txt").writeText("secret")
            },
            command = """rm unknown/message.txt""",
            isAllowed = false,
            expectedReason = { "unknown" },
            assertFailure = { workspace, _ ->
                assertTrue(workspace.resolve("unknown/message.txt").exists())
            }
        )
    }

    @Test
    fun `execute access is enforced by sandbox`() {
        assertAllowedScenario(
            scenarioName = "execute-allowed",
            accessType = FsAccessType.EXECUTE,
            additionalAccessTypes = listOf(FsAccessType.READ),
            relativeDir = "scripts",
            setup = { workspace ->
                workspace.resolve("scripts/run.sh").writeText("#!/usr/bin/env bash\nprintf 'ran-script'")
            },
            command = """./scripts/run.sh""",
            assertSuccess = { _, result ->
                assertEquals("ran-script", result.stdout.trim())
            }
        )

        assertBlockedScenario(
            scenarioName = "execute-denied",
            accessType = FsAccessType.EXECUTE,
            additionalAccessTypes = listOf(FsAccessType.READ),
            relativeDir = "scripts",
            setup = { workspace ->
                workspace.resolve("scripts/run.sh").writeText("#!/usr/bin/env bash\nprintf 'ran-script'")
            },
            command = """./scripts/run.sh""",
            isAllowed = false,
            expectedReason = { deniedDirText -> "denied:$deniedDirText" }
        )

        assertBlockedScenario(
            scenarioName = "execute-unknown",
            accessType = null,
            relativeDir = "scripts",
            setup = { workspace ->
                workspace.resolve("scripts/run.sh").writeText("#!/usr/bin/env bash\nprintf 'ran-script'")
            },
            command = """./scripts/run.sh""",
            isAllowed = false,
            expectedReason = { "unknown" }
        )
    }

    private fun sandboxService(workspace: Path, snapshot: PathPolicySnapshot): DockerSandboxService {
        return DockerSandboxService(
            config = SandboxConfig(
                dockerCommand = "docker",
                image = "arch-sandbox:latest",
                workingDirectoryHostPath = workspace,
                backingContainerPath = "/arch/backing",
                workspaceContainerPath = "/arch/workspace",
                containerNamePrefix = "arch-sandbox-test",
                hostPortStart = ThreadLocalRandom.current().nextInt(38090, 58000),
                containerPort = 18080
            ),
            pathPolicyLogic = FixedPathPolicyLogic(snapshot),
            agentId = UUID.randomUUID().toString()
        )
    }

    private fun pathSnapshot(vararg policies: PathPolicy): PathPolicySnapshot {
        return PathPolicySnapshot(
            policies = policies.asList(),
            deniedPathPrefixes = emptyList()
        )
    }

    private fun assertAllowedScenario(
        scenarioName: String,
        accessType: FsAccessType,
        additionalAccessTypes: List<FsAccessType> = emptyList(),
        relativeDir: String,
        command: String,
        setup: (Path) -> Unit = {},
        assertSuccess: (Path, SandboxShellResult) -> Unit
    ) {
        val workspace = tempDir.resolve(scenarioName).createDirectories()
        val scopedDir = workspace.resolve(relativeDir).createDirectories()
        setup(workspace)
        val service = sandboxService(
            workspace = workspace,
            snapshot = pathSnapshot(
                PathPolicy(
                    pattern = scopedDir.toAbsolutePath().normalize().toString(),
                    accessTypes = (listOf(accessType) + additionalAccessTypes).toMutableList(),
                    lifetime = PolicyLifetime.SESSION,
                    isAllowed = true,
                    reason = "Allowed for test"
                )
            )
        )

        service.use {
            it.start()
            val result = it.exec(command, 30)
            assertEquals(0, result.exitCode, result.toString())
            assertTrue(result.blockedOperations.isEmpty(), result.blockedOperations.toString())
            assertSuccess(workspace, result)
        }
    }

    private fun assertBlockedScenario(
        scenarioName: String,
        accessType: FsAccessType?,
        additionalAccessTypes: List<FsAccessType> = emptyList(),
        relativeDir: String,
        command: String,
        isAllowed: Boolean,
        expectedReason: (String) -> String,
        setup: (Path) -> Unit = {},
        assertFailure: (Path, SandboxShellResult) -> Unit = { _, _ -> }
    ) {
        val workspace = tempDir.resolve(scenarioName).createDirectories()
        val scopedDir = workspace.resolve(relativeDir).createDirectories()
        val scopedDirText = scopedDir.renderForAssertion()
        setup(workspace)
        val snapshot = if (accessType == null) {
            pathSnapshot()
        } else {
            pathSnapshot(
                PathPolicy(
                    pattern = scopedDir.toAbsolutePath().normalize().toString(),
                    accessTypes = (listOf(accessType) + additionalAccessTypes).toMutableList(),
                    lifetime = PolicyLifetime.SESSION,
                    isAllowed = isAllowed,
                    reason = "Denied for test"
                )
            )
        }
        val service = sandboxService(workspace = workspace, snapshot = snapshot)

        service.use {
            it.start()
            val result = it.exec(command, 30)
            assertTrue(result.exitCode != 0, result.toString())
            assertTrue(result.stderr.contains("Sandbox policy blocked filesystem access:"), result.stderr)
            assertTrue(
                result.blockedOperations.any { blocked ->
                    blocked.contains(scopedDirText) &&
                        blocked.contains("(${expectedReason(scopedDirText)})")
                },
                result.blockedOperations.toString()
            )
            assertFailure(workspace, result)
        }
    }

    private fun Path.renderForAssertion(): String {
        return toAbsolutePath().normalize().toString().replace('\\', '/')
    }
}

private class FixedPathPolicyLogic(
    private val snapshot: PathPolicySnapshot
) : PathPolicyLogic {
    override fun inspectPath(rawFilePath: String): PathAccessInspection {
        error("inspectPath is not used by DockerSandboxServiceTest")
    }

    override fun evaluate(rawFilePath: String, accessType: FsAccessType): PathPolicyResult {
        error("evaluate is not used by DockerSandboxServiceTest")
    }

    override fun activePathPolicies(): List<PathPolicy> = snapshot.policies

    override fun snapshot(): PathPolicySnapshot = snapshot

    override fun renderAgentPolicySummary(): String = ""

    override fun reloadFromPersistence() = Unit
}
