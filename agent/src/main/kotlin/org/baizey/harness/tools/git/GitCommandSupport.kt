package org.baizey.harness.tools.git

import org.baizey.runtime.ErrorLog

object GitCommandSupport {
    fun execute(gitDir: String, vararg args: String): Result<String> {
        val command = buildList {
            add("git")
            addAll(args.toList())
        }
        return try {
            val processBuilder = ProcessBuilder(command)
                .directory(java.io.File(gitDir))
                .redirectErrorStream(true)
            val process = processBuilder.start()
            val output = process.inputStream.readAllBytes().decodeToString().trim()
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(output.ifEmpty { "(empty output)" })
            } else {
                Result.failure(GitException("Command exited with code $exitCode: $output"))
            }
        } catch (e: Exception) {
            ErrorLog.log(
                source = "git_command",
                exception = e,
                context = mapOf(
                    "directory" to gitDir,
                    "command" to command.joinToString(" ")
                )
            )
            Result.failure(e)
        }
    }

    fun findGitRoot(startDir: String): String? {
        var dir = java.io.File(startDir).absoluteFile
        repeat(10) {
            if (java.io.File(dir, ".git").exists()) return dir.absolutePath
            dir = dir.parentFile ?: return null
        }
        return null
    }
}

class GitException(message: String) : Exception(message)
