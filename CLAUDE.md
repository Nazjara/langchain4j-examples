# CLAUDE.md

Multi-module Maven showcase of LangChain4j features, using the sample domains of `../spring-ai-examples` (Spring AI 2.0) as material. Java 25, Spring Boot 4.1, LangChain4j 1.20.2. Chat runs on Claude (Anthropic); OpenAI is used only where Anthropic has no equivalent (image generation, TTS). Each module is an independent Spring Boot app.

The user is new to LangChain4j. `PLAN.md` is the roadmap: follow its per-phase workflow (lesson → build → run → compare → checkpoint) and tick its §6 verify-checklist as APIs are confirmed against the jars.

## Build & run

- Build all: `./mvnw clean install` (tests hit live model APIs — use `-DskipTests` when no key/credits). Needs JDK 25: the shell default may be 21, so prefix with `JAVA_HOME=/usr/lib/jvm/openjdk-25`.
- Run a module: `cd <module> && ../mvnw spring-boot:run`
- Required env: `ANTHROPIC_API_KEY` (all modules except `audio`, `mcp-server` and `mcp-http-server`); `OPENAI_API_KEY` for `audio` and for `image`'s `POST /image` only (`image` starts without it, so `POST /vision` works on the Anthropic key alone); `API_NINJAS_API_KEY` for `functions` and for `mcp-server`'s `currentWeather` calls (the server starts without it; the agent's `mcp-server` subprocess inherits it). `mcp-http-server` needs no key (Open-Meteo)

## Layout

Root `pom.xml` is the parent: Spring Boot parent, `langchain4j-bom` (`langchain4j.version`), and dependencies shared by all modules (webmvc, `langchain4j-spring-boot4-starter`, `langchain4j-anthropic-spring-boot4-starter`, Lombok + its annotation processor path). Module poms only add module-specific deps. Never re-add `maven.compiler.*` properties to module poms — they override `java.version`.

- Versions: stable artifacts (core, `langchain4j-anthropic`, `-open-ai`) are `1.20.2`; newer ones (starters, `-agentic`, `-mcp`, embeddings, stores) are `1.20.2-beta30`. Both are managed by the BOM — never hardcode them in module poms. Community artifacts (e.g. `langchain4j-community-mcp-server`) need `langchain4j-community-bom`, imported only in the module that uses it.
- Spring Boot 4 needs the `*-spring-boot4-starter` artifacts, not `*-spring-boot-starter` (that's Boot 3).

| Module | Entry points | LangChain4j surface |
|---|---|---|
| `basics` | `POST /ask`, `POST /ask/result`, `GET /capital`, `GET /capital/details`, `GET /capitals` | `@AiService`, `@UserMessage(fromResource)`, `@V`, return-type structured output, `Result<T>`, custom `ChatModel` with `RESPONSE_FORMAT_JSON_SCHEMA` |
| `prompt-engineering` | Tests only (no endpoints, no Spring context) | Test-local AI Services via `AiServices.create`, `@SystemMessage`, `Result<T>.finalResponse()`, Anthropic thinking/effort/caching, few-shot via `ChatModel.chat(messages)` |
| `functions` | `POST /weather`, `POST /weather/tool-calls` | `@Tool`/`@P` bean auto-wired into `@AiService`, `Result.toolExecutions()`; stub-model test for `ToolSpecification` + `ToolExecutor`, `maxToolCallingRoundTrips`, `toolExecutionErrorHandler` |
| `rag` | `POST /ask/augmented`, `POST /ask/routed` | `ApacheTikaDocumentParser` → `EmbeddingStoreIngestor` (+ whitespace `DocumentTransformer`), `InMemoryEmbeddingStore` / `MilvusV2EmbeddingStore` (`prod`), local MiniLM; sources (`ai.rag.sources.*`) as metadata-filtered `EmbeddingStoreContentRetriever`s; two `DefaultRetrievalAugmentor` beans (`DefaultQueryRouter` / `LanguageModelQueryRouter`) wired into `@AiService`s with `wiringMode = EXPLICIT`; `Result.sources()` |
| `chat-memory` | `POST /chat/{id}`, `POST /chat/{id}/stream` (SSE), `GET /chat/{id}`, `DELETE /chat/{id}` | `@MemoryId`, `ChatMemoryProvider` → `MessageWindowChatMemory`, community `SQLChatMemoryStore` on Postgres (Boot Docker Compose), `Flux<String>` via `langchain4j-reactor` + `StreamingChatModel` |
| `image` | `POST /image`, `POST /vision` (multipart) | OpenAI starter's `ImageModel` (`gpt-image-1`, `langchain4j.open-ai.image-model.*`), `ImageContent` parameter on an `@AiService` method (Claude vision); stub-model test for the assembled `UserMessage` |
| `audio` | `POST /audio` | Hand-built `OpenAiTextToSpeechModel` (`@Experimental`, no starter properties, `ai.openai.*`), `TextToSpeechModel.synthesize` |
| `mcp-server` | None (stdio MCP server, no web server; started by an MCP client as a subprocess) | `langchain4j-community-mcp-server`: `McpServer` over `@Tool` beans + `StdioMcpServerTransport`, tool title/hints via `@Tool(metadata)`; tools only, logs on stderr (`logback-spring.xml`) |
| `mcp-http-server` | `POST /mcp` on port 8090 (stateless Streamable HTTP MCP server; `GET /mcp` → 405) | Not LangChain4j: MCP Java SDK 2.1.0 (`mcp-bom`, imported in this module only) — `HttpServletStatelessServerTransport` via `ServletRegistrationBean`, `McpServer.sync(...)` → `McpStatelessSyncServer`; Open-Meteo tools `forecast`/`airQuality`, resources `weather://wmo-codes` + template `weather://city/{name}`, prompt `trip-weather-brief`; `Origin` check, no auth |
| `mcp-agent` | `POST /ask`, `GET /tools`, `GET /resources`, `POST /resources/ask`, `GET /prompts`, `POST /prompts/{name}` | `langchain4j-mcp`: two `DefaultMcpClient`s (`api-ninjas` over `StdioMcpTransport`, `open-meteo` over `StreamableHttpMcpTransport`) → one `McpToolProvider` auto-wired into `@AiService`, with `SkippingResourcesAsToolsPresenter` (resources as tools); direct `readResource` / `getPrompt` + `McpPromptMessage.toChatMessage()` against the HTTP server |
| `agentic-tools` | `POST /ask?toolSearch=NONE\|SIMPLE\|VECTOR` (`X-User-Id`), `POST /plan`, `POST /ask/restricted` + `POST /ask/stream` (SSE; `X-Role`), `POST /ask/skills` | `AiServices.builder` in `@Configuration` (starter can't set these): `toolSearchStrategy` (`SimpleToolSearchStrategy`, `VectorToolSearchStrategy` + local MiniLM), `@Tool(searchBehavior = ALWAYS_VISIBLE)`, `maxToolCallingRoundTrips`, `InvocationParameters` injected into tools, `OutputGuardrail` (`JsonExtractorOutputGuardrail` subclass) with `reprompt` over a throwaway chat memory; `Result` usage incl. Anthropic cache tokens; `ToolProvider` (`RoleToolProvider`: tool set per `Role` from `InvocationParameters`, via `ToolService.findTools`), `hallucinatedToolNameStrategy`, `TokenStream` → typed SSE events with `StreamingHandle` cancel; `langchain4j-skills`: `ClassPathSkillLoader` (`resources/skills/*/SKILL.md`) → `Skills.toolProvider()` (`activate_skill`, `read_skill_resource`) next to the direct tools, skill-scoped tools attached in `SkillsConfig`, `formatAvailableSkills()` via `systemMessageTransformer` |
| `observability` | `POST /ask` (answer + `Result.sources()` + `traceId`) | `ObservationChatModelListener` bean (auto-injected `ChatModelListener`), own Micrometer listeners on LangChain4j hooks: `AiServiceListener` events (parent span), `beforeToolExecution`/`afterToolExecution` (tool spans), `ContentRetrieverListener` on an own `EmbeddingStoreContentRetriever` bean (retrieval span); `AiServices.builder` in `@Configuration`; OTLP to `grafana/otel-lgtm` via Boot Docker Compose |

Package convention per module (`com.nazjara`): `rest/QuestionController`, `service/` (`@AiService` interfaces; `AiServiceImpl` only when there is real orchestration), `model/` records, `configuration/`, `bootstrap/`, `tool/`.

## Conventions

- Explore LangChain4j, don't port Spring AI: design each module around LangChain4j's own capabilities and its idiomatic path. Reuse the matching `spring-ai-examples` module's name, sample data, prompts and env vars where convenient, but never add endpoints, layers or workarounds just to match its shape. `COMPARISON.md` records differences as a by-product.
- Claude models: `claude-sonnet-5-5` is the minimum; never Haiku, even where the Spring AI module uses it.
- A module that defines its own `ChatModel` bean (e.g. `basics`, for native JSON-schema output) reads its settings from `ai.anthropic.*`, not `langchain4j.anthropic.chat-model.*`: setting the starter's `api-key` registers a second `ChatModel` and breaks `@AiService` wiring.
- A module that combines the Anthropic and OpenAI starters (e.g. `image`) must exclude `langchain4j-http-client-jdk` from both: the OpenAI starter adds the Spring RestClient HTTP client, and the Anthropic starter's model then fails at startup with "Conflict: multiple HTTP clients".
- OpenAI image models: use `gpt-image-*`; the DALL·E constants in `OpenAiImageModelName` are deprecated.
- Java records for DTOs; Lombok for `@Slf4j` / `@RequiredArgsConstructor`. Record fields the model fills get `@Description`.
- Educational project: Javadoc every class and public method in the API (`rest/`), service, client and config/bootstrap layers. Explain *what LangChain4j does under the hood* (AI Service proxy, tool loop, retrieval augmentor, memory provider) and name the Spring AI equivalent. Models/records don't need it. Verify with `./mvnw javadoc:javadoc -Ddoclint=all,-missing -Dshow=private`.
- Prompt templates live in `src/main/resources/prompts/*.txt` using LangChain4j `{{var}}` syntax, referenced via `@SystemMessage`/`@UserMessage(fromResource = "/prompts/...")`.
- Model settings go in each module's `application.properties` under `langchain4j.anthropic.chat-model.*`.
- `@AiService` auto-wiring fails on duplicate beans of the same type; use `wiringMode = EXPLICIT` or `AiServices.builder(...)` in `@Configuration` when a module needs two differently wired services.
- After each module: add its row(s) to `COMPARISON.md` and its section to `README.md`.
- Each module has `<module>/<module>.http` (IntelliJ HTTP Client) with one request per endpoint, using a `@host` variable. This is how the user runs requests.
- New feature = new module registered in root `<modules>`.
- Observability: Micrometer's current observation is thread-local, so listener spans nest only on blocking AI Services (no streaming/async, no `executeToolsConcurrently()`, one query and one retriever). Don't register `MicrometerMetricsChatModelListener` next to `ObservationChatModelListener`: both record `gen_ai.client.token.usage`.
- MCP naming: an app that calls MCP servers is named `*-agent`, never `*-client` or `*-host`.
- MCP: LangChain4j's server (`langchain4j-community-mcp-server`) is stdio-only and serves tools only. `mcp-agent` starts the `mcp-server` jar as a subprocess (`ai.mcp.server.jar`), so build `mcp-server` first; `mcp-server`'s stdout is the protocol channel, so nothing there may log to stdout. `mcp-agent` also connects to `mcp-http-server` at startup (`ai.mcp.http-server.url`), so start that first. JSON-RPC traffic is logged on the logger named `MCP`.
- MCP Java SDK 2.1: use the builders that take the required fields (`Tool.builder(name, schema)`, `Resource.builder(uri, name)`, `GetPromptResult.builder(messages)`); the no-argument builders and short record constructors are deprecated. Tool failures go back as `isError` results, not exceptions.
- No Spring AI at runtime: a module never connects to or depends on anything from `spring-ai-examples` (confirmed for B6: `mcp-http-server` uses the MCP Java SDK, not Spring AI). When LangChain4j lacks a piece, keep the LangChain4j side that exists, document the gap in Javadoc and `COMPARISON.md`, and move on.
