# Arch

`Arch` is a local Kotlin/JVM agent harness for running an Ollama-backed software assistant through a browser console. The app starts a small built-in HTTP server, serves the console UI, and routes model turns through LangChain4j with built-in tools for filesystem work, web search/fetching, git operations, and asking the user for decisions.

The default assistant is named `Arch`.

## Features

- Browser console for sending prompts, watching model activity, and responding to permission or clarification requests.
- Local Ollama chat models via LangChain4j.
- Built-in filesystem tools with persisted path permission policies.
- Built-in git tools with policy checks.
- Web search and website fetching with optional Brave, Bing, or Google Custom Search credentials.
- Event filter profiles for controlling what activity is shown in the console.
- Tool filter profiles for enabling or disabling built-in tool groups.
- Optional stdio MCP servers loaded from local configuration.

## Requirements

- JDK 25. The Gradle build uses the Foojay toolchain resolver, so Gradle may provision the toolchain when needed.
- Ollama running locally or at the URL configured in `.env`.
- The Ollama model selected in the UI must exist in your Ollama instance.
- Network access for the first Gradle dependency download and for any configured web search provider.

Supported model names are currently defined in `ModelSelection`:

- `qwen3.5:9b`
- `qwen3.6:27b` (default)
- `qwen3.6:35b`

## Setup

Create a local environment file:

```powershell
Copy-Item .env.example .env
```

Edit `.env`:

```dotenv
AGENT_CONSOLE_HOST=127.0.0.1
AGENT_CONSOLE_PORT=8420
OLLAMA_BASE_URL=http://localhost:11434
ARCH_HOME=

BING_SEARCH_API_KEY=
BRAVE_SEARCH_API_KEY=
GOOGLE_SEARCH_API_KEY=
GOOGLE_SEARCH_ENGINE_ID=
```

Notes:

- `ARCH_HOME` is optional. When empty, the app uses `~/.arch`.
- At least one of Brave, Bing, or Google search credentials is needed for web search. Website fetching still works without search credentials.
- Gradle tasks currently require `.env` because `agent/build.gradle.kts` reads it during project configuration.

## Run

Start the web console:

```powershell
.\gradlew.bat :agent:run
```

Then open:

```text
http://127.0.0.1:8420
```

Use the configured host and port if you changed them in `.env`.

To build a self-contained agent jar:

```powershell
.\gradlew.bat :agent:bundleAgentJar
```

To build and run that jar:

```powershell
.\gradlew.bat :agent:runBundledAgentJar
```

If a bundled agent from this project is already running on the configured port, `runBundledAgentJar` asks it to shut down. If the port is occupied by another process, the task fails.

## Test

Run the full test suite:

```powershell
.\gradlew.bat test
```

Run only the agent module tests:

```powershell
.\gradlew.bat :agent:test
```

JUnit parallel execution is enabled in `agent/src/test/resources/junit-platform.properties`.

## Persistent Data

Runtime state is stored under `ARCH_HOME`, or `~/.arch` when `ARCH_HOME` is empty.

Important paths:

- `system/config/path_policy.json`: persisted filesystem permission decisions.
- `system/config/git_policy.json`: persisted git permission decisions.
- `system/config/activity_filter_profiles.json`: console event filter profiles.
- `system/config/tool_filter_profiles.json`: tool availability profiles.
- `system/config/mcp_config.json`: optional MCP server configuration.
- `system/logs/audit.log`: policy decision audit log.
- `system/logs/errors.log`: runtime error log.

The app treats `system/` under this home directory as protected agent state.

## AgentSH Sandbox Image

The repo includes an AgentSH runner image for one-container-per-agent execution.

Build it:

```powershell
docker build -t arch-agentsh:latest .\infra\agentsh
```

Smoke-test it without an agent:

```powershell
docker run -d --privileged --name arch-agentsh-smoke -p 18080:18080 -e AGENTSH_API_KEY=sk-local-smoke-test arch-agentsh:latest
Invoke-WebRequest -UseBasicParsing -Uri http://127.0.0.1:18080/health -Headers @{ 'X-API-Key' = 'sk-local-smoke-test' }
docker exec arch-agentsh-smoke /usr/bin/agentsh --api-key sk-local-smoke-test session create --workspace /workspace
```

The included `sk-local-smoke-test` API key is only for local container smoke tests. The `--privileged` flag is required for the current FUSE/seccomp smoke-test path.

Enable sandbox settings in `.env`:

```dotenv
AGENT_SANDBOX_ENABLED=true
AGENT_SANDBOX_IMAGE=arch-agentsh:latest
AGENT_SANDBOX_API_KEY=sk-local-smoke-test
AGENT_SANDBOX_HOST_ROOT=C:\
AGENT_SANDBOX_CONTAINER_HOST_ROOT=/host
AGENT_SANDBOX_PORT_START=18080
```

`AgentSandboxManager` starts containers named `arch-agentsh-<agent-id>`, mounts the host root at `/host`, mounts per-agent logs under `ARCH_HOME/system/logs/agentsh`, and exposes each AgentSH server on the next available port from `AGENT_SANDBOX_PORT_START`.

Per-agent policy is selected with `AGENTSH_POLICY_NAME` inside each container. Policies live under `<ARCH_HOME>/system/sandbox/policies`. The image includes a permissive audit-first `default` policy for initial local testing.

When sandboxing is enabled, the built-in `shell` tool executes commands through AgentSH and the built-in `ask_path_permission` tool can prompt the user for a new filesystem allowance or denial, then rebuild the per-agent AgentSH policy before the next sandbox session is used.

## MCP Configuration

Optional MCP servers are loaded from:

```text
<ARCH_HOME>/system/config/mcp_config.json
```

Expected shape:

```json
{
  "servers": {
    "example": {
      "type": "stdio",
      "env": {},
      "command": "example-command",
      "args": ["--flag"]
    }
  }
}
```

Each server is launched as a stdio MCP client and exposed to the model through LangChain4j.

## Project Layout

```text
.
+-- agent/
|   +-- src/main/kotlin/org/baizey/harness/   # session loop, system prompt, policies, tool context
|   +-- src/main/kotlin/org/baizey/runtime/   # app config, Ollama runtime, persistence, model selection
|   +-- src/main/kotlin/org/baizey/web/       # HTTP server, API routes, console interaction port
|   +-- src/main/resources/web/               # browser console HTML/CSS/JS and fonts
|   +-- src/test/kotlin/                      # unit and contract tests
+-- build.gradle.kts                          # shared Kotlin plugin versions
+-- settings.gradle.kts                       # Gradle root and module includes
+-- .env.example                              # local runtime configuration template
```

## License

This project is licensed under GPL-3.0. See `LICENSE`.
