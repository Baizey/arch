import org.gradle.api.GradleException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.serialization") version "2.3.10"
    application
}

group = "org.baizey"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.langchain4j:langchain4j:1.13.0")
    implementation("dev.langchain4j:langchain4j-open-ai:1.13.0")
    implementation("dev.langchain4j:langchain4j-ollama:1.13.0")
    implementation("dev.langchain4j:langchain4j-mcp:1.13.0-beta23")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        javaParameters.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.baizey.web.AgentConsoleLauncherKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

private fun loadDotEnv(projectDir: File): Map<String, String> {
    val envFile = projectDir.resolve(".env")
    if (!envFile.exists()) {
        throw GradleException("Missing .env file at ${envFile.absolutePath}")
    }

    return envFile.readLines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                null
            } else {
                val declaration = trimmed.removePrefix("export ").trim()
                val delimiterIndex = declaration.indexOf('=')
                if (delimiterIndex <= 0) {
                    null
                } else {
                    val key = declaration.substring(0, delimiterIndex).trim()
                    val rawValue = declaration.substring(delimiterIndex + 1).trim()
                    val value = if (rawValue.length >= 2 && rawValue.first() == rawValue.last() &&
                        (rawValue.first() == '"' || rawValue.first() == '\'')
                    ) {
                        rawValue.substring(1, rawValue.lastIndex)
                    } else {
                        rawValue
                    }
                    key.takeIf { it.isNotEmpty() }?.let { it to value }
                }
            }
        }
        .toMap(LinkedHashMap())
}

private val dotEnv = loadDotEnv(rootProject.projectDir)

private fun requiredDotEnv(name: String): String {
    return dotEnv[name]?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw GradleException(".env must define $name.")
}

private fun requiredIntDotEnv(name: String): Int {
    val rawValue = requiredDotEnv(name)
    return rawValue.toIntOrNull()
        ?: throw GradleException("$name must be an integer.")
}

private val AGENT_HOST = requiredDotEnv("AGENT_CONSOLE_HOST")
private val AGENT_PORT = requiredIntDotEnv("AGENT_CONSOLE_PORT")
private val AGENT_SERVICE_ID = "arch-web-harness"
private val AGENT_REQUEST_TIMEOUT_MS = 1_000
private val AGENT_SHUTDOWN_TIMEOUT_MS = 10_000L

private enum class BundledAgentStatus {
    STOPPED,
    RUNNING,
    PORT_OCCUPIED
}

private fun openAgentConnection(path: String, method: String): HttpURLConnection {
    return (URI("http://$AGENT_HOST:$AGENT_PORT$path").toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = AGENT_REQUEST_TIMEOUT_MS
        readTimeout = AGENT_REQUEST_TIMEOUT_MS
    }
}

private fun detectBundledAgentStatus(): BundledAgentStatus {
    val connection = try {
        openAgentConnection("/api/meta", "GET")
    } catch (_: ConnectException) {
        return BundledAgentStatus.STOPPED
    } catch (_: SocketTimeoutException) {
        return BundledAgentStatus.PORT_OCCUPIED
    } catch (_: IOException) {
        return BundledAgentStatus.PORT_OCCUPIED
    }

    return try {
        if (connection.responseCode != 200) {
            BundledAgentStatus.PORT_OCCUPIED
        } else {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            if (body.contains("\"service\":\"$AGENT_SERVICE_ID\"")) {
                BundledAgentStatus.RUNNING
            } else {
                BundledAgentStatus.PORT_OCCUPIED
            }
        }
    } catch (_: ConnectException) {
        BundledAgentStatus.STOPPED
    } catch (_: SocketTimeoutException) {
        BundledAgentStatus.PORT_OCCUPIED
    } catch (_: IOException) {
        BundledAgentStatus.PORT_OCCUPIED
    } finally {
        connection.disconnect()
    }
}

private fun requestBundledAgentShutdown() {
    val connection = openAgentConnection("/api/shutdown", "POST")
    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw GradleException("Running agent did not accept shutdown request (HTTP $responseCode).")
        }
    } finally {
        connection.disconnect()
    }
}

private fun waitForBundledAgentToStop(timeoutMs: Long): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        if (detectBundledAgentStatus() == BundledAgentStatus.STOPPED) {
            return true
        }
        Thread.sleep(200)
    }
    return detectBundledAgentStatus() == BundledAgentStatus.STOPPED
}

val javaToolchains = extensions.getByType(JavaToolchainService::class.java)
val bundledAgentJar = tasks.jar
val bundledAgentJarFile = bundledAgentJar.flatMap { it.archiveFile }

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    }) {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

tasks.register("bundleAgentJar") {
    group = "build"
    description = "Bundles the agent module and runtime dependencies into an executable jar."
    dependsOn(bundledAgentJar)
    outputs.file(bundledAgentJarFile)
}

tasks.register("runBundledAgentJar") {
    group = "application"
    description = "Builds and runs the bundled agent jar, or stops it if it is already running."
    dependsOn("bundleAgentJar")
    inputs.file(bundledAgentJarFile)
    doLast {
        when (detectBundledAgentStatus()) {
            BundledAgentStatus.RUNNING -> {
                logger.lifecycle("Agent web harness is already running on http://$AGENT_HOST:$AGENT_PORT. Requesting graceful shutdown.")
                requestBundledAgentShutdown()
                if (!waitForBundledAgentToStop(AGENT_SHUTDOWN_TIMEOUT_MS)) {
                    throw GradleException("Timed out waiting for the running agent to stop.")
                }
                logger.lifecycle("Agent web harness stopped.")
            }

            BundledAgentStatus.PORT_OCCUPIED -> {
                throw GradleException("Port $AGENT_PORT is already in use by a different process.")
            }

            BundledAgentStatus.STOPPED -> {
                val command = mutableListOf(
                    javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
                ).apply {
                    addAll(application.applicationDefaultJvmArgs ?: emptyList())
                    add("-jar")
                    add(bundledAgentJarFile.get().asFile.absolutePath)
                }
                val exitCode = ProcessBuilder(command)
                    .directory(rootProject.projectDir)
                    .inheritIO()
                    .start()
                    .waitFor()
                if (exitCode != 0) {
                    throw GradleException("Bundled agent jar exited with code $exitCode.")
                }
            }
        }
    }
}
