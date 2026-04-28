package org.baizey.runtime

object ErrorLog {
    fun log(
        source: String,
        exception: Throwable,
        context: Map<String, String> = emptyMap()
    ) {
        AppLog.writeError(
            event = source,
            context = context,
            exception = exception
        )
    }
}
