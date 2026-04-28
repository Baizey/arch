package org.baizey.utils

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.readText

object IO {
    val json = Json { ignoreUnknownKeys = true }
    private val binaryExtensions = setOf("class", "jar", "zip", "png", "jpg", "exe", "so", "dylib", "pdf")

    inline fun <reified T> String.fromJson(): T {
        return json.decodeFromString<T>(this)
    }

    inline fun <reified T> T.toJson(): String {
        return json.encodeToString<T>(this)
    }

    fun Path.canReadAsText(): Boolean {
        return isRegularFile() && extension.lowercase() !in binaryExtensions
    }

    fun Path.readIfExists(): String? {
        if (this.notExists()) return null
        return this.readText()
    }
}