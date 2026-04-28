package org.baizey.runtime

import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import kotlin.io.path.createParentDirectories

internal object AppLog {
    fun writeError(
        event: String,
        context: Map<String, String> = emptyMap(),
        exception: Throwable
    ) {
        appendEntry(
            path = SystemPath.errorLogFile,
            category = "error",
            event = event,
            context = context,
            exception = exception
        )
    }

    fun writeAudit(
        event: String,
        context: Map<String, String> = emptyMap()
    ) {
        appendEntry(
            path = SystemPath.auditLogFile,
            category = "audit",
            event = event,
            context = context,
            exception = null
        )
    }

    private fun appendEntry(
        path: Path,
        category: String,
        event: String,
        context: Map<String, String>,
        exception: Throwable?
    ) {
        runCatching {
            path.createParentDirectories()
            Files.writeString(
                path,
                buildEntry(category, event, context, exception),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.APPEND
            )
        }
    }

    private fun buildEntry(
        category: String,
        event: String,
        context: Map<String, String>,
        exception: Throwable?
    ): String {
        return buildString {
            appendLine("timestamp: ${Instant.now()}")
            appendLine("category: $category")
            appendLine("event: $event")
            if (context.isNotEmpty()) {
                appendLine("context:")
                context.entries.sortedBy { it.key }.forEach { (key, value) ->
                    appendLine("  $key: ${value.replace('\n', ' ')}")
                }
            }
            exception?.let {
                appendLine("exception: ${it::class.java.name}")
                appendLine("message: ${it.message ?: "<none>"}")
                appendLine("stacktrace:")
                appendLine(stackTraceString(it))
            }
            appendLine("---")
        }
    }

    private fun stackTraceString(exception: Throwable): String {
        return StringWriter().also { writer ->
            exception.printStackTrace(PrintWriter(writer))
        }.toString().trimEnd()
    }
}
