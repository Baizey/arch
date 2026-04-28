package org.baizey.runtime

import dev.langchain4j.model.output.Response

interface Assistant {
    fun chat(userMessage: String): String?
}
