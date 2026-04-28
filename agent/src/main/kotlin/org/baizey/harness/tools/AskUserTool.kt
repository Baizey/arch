package org.baizey.harness.tools

import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import org.baizey.harness.HarnessInteractionPort

class AskUserTool(
    private val interactionPort: HarnessInteractionPort
) {
    @Tool(
        name = "ask_user",
        value = ["""Ask the user a multiple-choice question when you genuinely need user input.
Provide short, distinct options. The UI also gives the user a built-in freeform override.
Example: ask_user(question="Which package manager should I use?", options=["pnpm","npm","yarn"])"""]
    )
    fun askUser(
        @P("Question to show the user.")
        question: String,
        @P("Predefined options to offer. The UI also adds its own freeform fallback automatically.")
        options: List<String>
    ): String {
        if (question.isBlank()) {
            return "Error, no question provided, you have to provide a question for this to matter"
        }
        val result = interactionPort.askUserQuestion(question, options)
        return if (result.isAccepted) {
            buildString {
                append("User selected a given option and choose: ")
                append(result.selection)
            }
        } else {
            buildString {
                append("User denied your question with the reason:")
                append(result.selection)
            }
        }
    }
}
