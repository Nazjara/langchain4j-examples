# CLAUDE.md

Multi-module Maven showcase of LangChain4j features, a side-by-side counterpart of `../spring-ai-examples` (Spring AI 2.0). Java 25, Spring Boot 4.1, LangChain4j 1.20.2. Chat runs on Claude (Anthropic); OpenAI is used only where Anthropic has no equivalent (image generation, TTS). Each module is an independent Spring Boot app.

The user is new to LangChain4j. `PLAN.md` is the roadmap: follow its per-phase workflow (lesson → build → run → compare → checkpoint) and tick its §6 verify-checklist as APIs are confirmed against the jars.

## Build & run

- Build all: `./mvnw clean install` (tests hit live model APIs — use `-DskipTests` when no key/credits). Needs JDK 25: the shell default may be 21, so prefix with `JAVA_HOME=/usr/lib/jvm/openjdk-25`.
- Run a module: `cd <module> && ../mvnw spring-boot:run`
- Required env: `ANTHROPIC_API_KEY` (all modules except `audio` and `mcp-server`); `OPENAI_API_KEY` for `image` and `audio`; `API_NINJAS_API_KEY` for `functions` and `mcp-server`

## Layout

Root `pom.xml` is the parent: Spring Boot parent, `langchain4j-bom` (`langchain4j.version`), and dependencies shared by all modules (webmvc, `langchain4j-spring-boot4-starter`, `langchain4j-anthropic-spring-boot4-starter`, Lombok + its annotation processor path). Module poms only add module-specific deps. Never re-add `maven.compiler.*` properties to module poms — they override `java.version`.

- Versions: stable artifacts (core, `langchain4j-anthropic`, `-open-ai`) are `1.20.2`; newer ones (starters, `-agentic`, `-mcp`, embeddings, stores) are `1.20.2-beta30`. Both are managed by the BOM — never hardcode them in module poms. Community artifacts (e.g. `langchain4j-community-mcp-server`) need `langchain4j-community-bom`, imported only in the module that uses it.
- Spring Boot 4 needs the `*-spring-boot4-starter` artifacts, not `*-spring-boot-starter` (that's Boot 3).

| Module | Entry points | LangChain4j surface |
|---|---|---|
| `basics` | `POST /ask`, `POST /ask/result`, `GET /capital`, `GET /capital/details`, `GET /capitals` | `@AiService`, `@UserMessage(fromResource)`, `@V`, return-type structured output, `Result<T>`, custom `ChatModel` with `RESPONSE_FORMAT_JSON_SCHEMA` |
| `prompt-engineering` | Tests only (no endpoints, no Spring context) | Test-local AI Services via `AiServices.create`, `@SystemMessage`, `Result<T>.finalResponse()`, Anthropic thinking/effort/caching, few-shot via `ChatModel.chat(messages)` |
| `functions` | `POST /weather`, `POST /weather/tool-calls` | `@Tool`/`@P` bean auto-wired into `@AiService`, `Result.toolExecutions()`; stub-model test for `ToolSpecification` + `ToolExecutor`, `maxToolCallingRoundTrips`, `toolExecutionErrorHandler` |

Package convention per module (`com.nazjara`): `rest/QuestionController`, `service/` (`@AiService` interfaces; `AiServiceImpl` only when there is real orchestration), `model/` records, `configuration/`, `bootstrap/`, `tool/`.

## Conventions

- Mirror the matching `spring-ai-examples` module: same module name, endpoints, request/response records and env vars, so differences come from the framework only.
- Claude models: `claude-sonnet-5-5` is the minimum; never Haiku, even where the Spring AI module uses it.
- A module that defines its own `ChatModel` bean (e.g. `basics`, for native JSON-schema output) reads its settings from `ai.anthropic.*`, not `langchain4j.anthropic.chat-model.*`: setting the starter's `api-key` registers a second `ChatModel` and breaks `@AiService` wiring.
- Java records for DTOs; Lombok for `@Slf4j` / `@RequiredArgsConstructor`. Record fields the model fills get `@Description`.
- Educational project: Javadoc every class and public method in the API (`rest/`), service, client and config/bootstrap layers. Explain *what LangChain4j does under the hood* (AI Service proxy, tool loop, retrieval augmentor, memory provider) and name the Spring AI equivalent. Models/records don't need it. Verify with `./mvnw javadoc:javadoc -Ddoclint=all,-missing -Dshow=private`.
- Prompt templates live in `src/main/resources/prompts/*.txt` using LangChain4j `{{var}}` syntax, referenced via `@SystemMessage`/`@UserMessage(fromResource = "/prompts/...")`.
- Model settings go in each module's `application.properties` under `langchain4j.anthropic.chat-model.*`.
- `@AiService` auto-wiring fails on duplicate beans of the same type; use `wiringMode = EXPLICIT` or `AiServices.builder(...)` in `@Configuration` when a module needs two differently wired services.
- After each module: add its row(s) to `COMPARISON.md` and its section to `README.md`.
- Each module has `<module>/<module>.http` (IntelliJ HTTP Client) with one request per endpoint, using a `@host` variable. This is how the user runs requests.
- New feature = new module registered in root `<modules>`.
- MCP naming: an app that calls MCP servers is named `*-agent`, never `*-client` or `*-host`.
