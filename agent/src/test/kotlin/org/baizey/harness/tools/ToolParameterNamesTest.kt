package org.baizey.harness.tools

import dev.langchain4j.agent.tool.ToolSpecifications
import org.baizey.harness.AskUserAnswer
import org.baizey.harness.HarnessInteractionPort
import org.baizey.harness.HarnessContext
import org.baizey.harness.PermissionDecision
import org.baizey.harness.PermissionRequest
import org.baizey.harness.policy.shared.PolicyLifetime
import org.baizey.harness.tools.fs.InspectPathAccessTool
import org.baizey.harness.tools.fs.ReadFileTool
import org.baizey.harness.tools.fs.SearchFilesTool
import org.baizey.harness.tools.fs.WriteFileTool
import org.baizey.harness.tools.web.FetchWebsiteTool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolParameterNamesTest {
    private val toolContext = HarnessContext(
        interactionPort = object : HarnessInteractionPort {
            override fun askUserQuestion(question: String, options: List<String>): AskUserAnswer {
                return AskUserAnswer(
                    isAccepted = true,
                    selection = options.firstOrNull().orEmpty(),
                    selectionIndex = 0
                )
            }

            override fun requestPermission(request: PermissionRequest): PermissionDecision {
                return PermissionDecision(
                    isAllowed = true,
                    lifetime = PolicyLifetime.ONCE,
                    scope = request.path
                )
            }
        }
    )

    @Test
    fun `write file method preserves parameter names for tool schema reflection`() {
        val names = WriteFileTool::class.java.methods
            .first { it.name == "writeFile" }
            .parameters
            .map { it.name }

        assertEquals(listOf("path", "content", "shouldOverwriteExistingFile"), names)
    }

    @Test
    fun `web tools also preserve parameter names for tool schema reflection`() {
        val names = FetchWebsiteTool::class.java.methods
            .first { it.name == "fetchWebsite" }
            .parameters
            .map { it.name }

        assertEquals(
            listOf("url", "startChar", "maxCharacters", "shouldSummarizeWithAi", "queryGoal"),
            names
        )
    }

    @Test
    fun `read file tool specification exposes the intended description and parameter names`() {
        val tool = ReadFileTool(toolContext.pathPolicyLogic)
        val specification = ToolSpecifications.toolSpecificationsFrom(tool)
            .single { it.name() == "read_file" }

        assertTrue(
            specification.description().startsWith("Read a slice of a file by 0-based line numbers."),
            specification.description()
        )
        assertFalse(specification.description().contains("org.baizey."), specification.description())
        assertEquals(
            listOf("path", "startLine", "endLineExclusive", "maxLines"),
            specification.parameters().properties().keys.toList()
        )
    }

    @Test
    fun `search files tool specification documents optional query behavior`() {
        val tool = SearchFilesTool(toolContext.pathPolicyLogic)
        val specification = ToolSpecifications.toolSpecificationsFrom(tool)
            .single { it.name() == "search_files" }

        assertTrue(
            specification.description().contains("If query is omitted or blank"),
            specification.description()
        )
        assertEquals(
            listOf(
                "path",
                "query",
                "glob",
                "shouldUseRegex",
                "shouldIgnoreCase",
                "maxMatches",
                "contextLines"
            ),
            specification.parameters().properties().keys.toList()
        )
    }

    @Test
    fun `inspect path access tool exposes the intended description and parameter name`() {
        val tool = InspectPathAccessTool(toolContext.pathPolicyLogic)
        val specification = ToolSpecifications.toolSpecificationsFrom(tool)
            .single { it.name() == "inspect_path_access" }

        assertTrue(
            specification.description().startsWith("Inspect current filesystem access for one path without prompting the user."),
            specification.description()
        )
        assertEquals(listOf("path"), specification.parameters().properties().keys.toList())
    }

    @Test
    fun `full built in tool schema does not leak reflection method signatures`() {
        val schemas = AgentTools.create(toolContext)
            .flatMap { ToolSpecifications.toolSpecificationsFrom(it) }
            .map { it.toJson() }

        assertTrue(schemas.isNotEmpty())
        schemas.forEach { schema ->
            assertFalse(schema.contains("void org.baizey."), schema)
            assertFalse(schema.contains("java.lang.reflect.Method"), schema)
            assertFalse(schema.contains("org.baizey.runtime.AuditLog.logPolicyDecision"), schema)
        }
    }
}
