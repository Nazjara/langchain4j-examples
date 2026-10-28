# LangChain4j Examples

Examples of using [LangChain4j](https://docs.langchain4j.dev) with Spring Boot, built as a side-by-side counterpart of [spring-ai-examples](../spring-ai-examples): same modules and endpoints, so the two frameworks can be compared directly. See [COMPARISON.md](COMPARISON.md).

## Prerequisites

- Java 25
- Maven (wrapper included)
- Anthropic API key (Claude) — all chat features
- OpenAI API key — only for image generation (`image`) and text-to-speech (`audio`)
- API Ninjas key — only for `functions` and the `currentWeather` tool of `mcp-server` (used by `mcp-agent`); `mcp-http-server` needs no key
- Docker — only for the `rag` `prod` profile (Milvus), `chat-memory` (PostgreSQL) and `observability` (Grafana LGTM)

## Setup

1. Clone the repository
2. Set your API keys as environment variables:
   ```
   export ANTHROPIC_API_KEY=your_anthropic_key
   export OPENAI_API_KEY=your_openai_key
   export API_NINJAS_API_KEY=your_api_ninjas_key
   ```
3. Build the project:
   ```
   ./mvnw clean install
   ```

## Modules

### basics
Plain Q&A, prompt templates and structured output through one `@AiService` interface (`Assistant`) with no implementation class. Structured output uses Claude's native JSON-schema mode, enabled by a hand-built `ChatModel` bean (`ChatModelConfig`). `POST /ask/result` is LangChain4j-only: it returns `Result<T>` metadata (token usage, finish reason).

### prompt-engineering
Prompt-design techniques as live JUnit tests, ported from spring-ai-examples: system prompts, few-shot examples, XML documents-first prompts, native structured output, prompt caching, and adaptive thinking with effort. The tests are plain JUnit with no Spring context, and each builds the Claude model it needs. This module has no endpoints; run `../mvnw test` from `prompt-engineering` (needs `ANTHROPIC_API_KEY`; tests are skipped without it).

### functions
Tool calling: a weather `@Tool` method (`WeatherTools`, backed by the API Ninjas `WeatherClient`) that the starter wires automatically into the `WeatherAssistant` AI Service. The model fetches live weather and converts sunrise and sunset to local time. `POST /weather/tool-calls` is LangChain4j-only: it also returns each tool call via `Result.toolExecutions()`. `ProgrammaticToolsTest` shows the `ToolSpecification` + `ToolExecutor` form, the round-trip cap and a custom error handler against a stub model, with no API key needed. Needs `ANTHROPIC_API_KEY` and `API_NINJAS_API_KEY`.

### rag
Retrieval-augmented generation over the same documents as spring-ai-examples (a tow-vehicle list and four Yamaha boat performance bulletins). On every start, the documents are parsed with Apache Tika, split into segments of about 256 tokens, embedded in-process with all-MiniLM-L6-v2, and kept in an `InMemoryEmbeddingStore` on the heap, which is lost on shutdown. The documents are split into two sources, `trucks` and `boats` (`ai.rag.sources.*`): every chunk is tagged with its source, and each source is searched separately for its own top 3, so the boat bulletins can't crowd the truck list out of the results. Both endpoints return the answer plus the chunks used, with their source.
- `POST /ask/augmented` (`RagAssistant`): every question searches every source (`DefaultQueryRouter`).
- `POST /ask/routed` (`RoutingRagAssistant`): Claude first picks the sources (`LanguageModelQueryRouter`, one extra short Claude call). "How much can the Chevy Colorado tow?" searches trucks only; if Claude's pick can't be used, every source is searched.

Retrieval runs inside the AI Service proxy, through a `DefaultRetrievalAugmentor` built in `RetrievalAugmentorConfig`. Under the `prod` profile, segments go to Milvus (`docker compose up -d` in `rag/` first). Needs `ANTHROPIC_API_KEY`.

### chat-memory
One conversation per id: `ChatAssistant` takes a `@MemoryId`, and the starter wires in a `ChatMemoryProvider` that builds a 20-message `MessageWindowChatMemory` for each id. Messages persist in PostgreSQL through LangChain4j's `SQLChatMemoryStore` (`langchain4j-community-sql`), one JSON row per conversation. Boot's Docker Compose support starts the database automatically. `POST /chat/{id}` answers in one response. `POST /chat/{id}/stream` returns the same answer as Server-Sent Events through the AI Service's `Flux<String>` method; the reply is stored when the model's stream completes. Closing the connection doesn't stop the Anthropic call in LangChain4j 1.20.2. `GET /chat/{id}` shows the stored window and `DELETE /chat/{id}` clears it. `MemoryWindowTest` shows the window and per-id isolation against a stub model, with no API key or database needed. Needs `ANTHROPIC_API_KEY` and Docker. Boot looks for `compose.yaml` in the working directory, so an IntelliJ run configuration needs its working directory set to `$MODULE_WORKING_DIR$`.

### image
Two kinds of model in one app. `POST /image` generates a picture with OpenAI's `gpt-image-1` through LangChain4j's `ImageModel`, which the OpenAI starter builds from `langchain4j.open-ai.image-model.*`; the image comes back as base64 and is returned as PNG. `POST /vision` takes a multipart upload and asks Claude to describe it: the `VisionAssistant` AI Service has an `ImageContent` parameter, which the proxy sends as an image block next to the prompt text. `QuestionControllerTest` runs both endpoints against stub models and shows the exact user message, with no API key needed. Needs `ANTHROPIC_API_KEY` for `POST /vision` and `OPENAI_API_KEY` for `POST /image` (OpenAI may require a verified organization for `gpt-image-1`); each endpoint works with its own key alone. The module's pom excludes LangChain4j's JDK HTTP client, because the Anthropic and OpenAI starters otherwise fail at startup with "multiple HTTP clients".

### audio
Text-to-speech: `POST /audio` turns text into MP3 with OpenAI's `gpt-4o-mini-tts` through LangChain4j's `TextToSpeechModel`. The OpenAI starter has no text-to-speech properties, so `TextToSpeechModelConfig` builds the model by hand from `ai.openai.*`. Only the model, the text and the voice can be set; speed, speaking instructions and the audio format cannot. Needs `OPENAI_API_KEY` only.

### mcp-server
A Model Context Protocol server built with `langchain4j-community-mcp-server`. It publishes the `currentWeather` tool, declared with the same `@Tool` annotation an AI Service uses; `@Tool(metadata)` adds the MCP title and the read-only/idempotent hints. LangChain4j's server speaks stdio only and serves tools only: it has no HTTP transport, no resources, no prompts and no notifications. The app therefore has no web server and is not run on its own. An MCP client on the same machine starts the jar as a subprocess and talks to it over stdin and stdout; it cannot be reached over the network or shared between clients. Stdout is the protocol channel, so the banner is off and all logging goes to stderr. `McpServerTest` drives the protocol handler directly and shows each response, including "Method not found" for resources, prompts and `ping`; no API key needed. The server starts without `API_NINJAS_API_KEY`; only `currentWeather` calls need it. `mcp-agent` keeps it as the "local plug-in" example next to `mcp-http-server`.

### mcp-http-server
A remotely reachable MCP server on `http://<host>:8090/mcp`, built with the official MCP Java SDK (`mcp-core` + `mcp-json-jackson3` 2.1.0) as a plain servlet in Spring Boot. It uses no LangChain4j and no Spring AI, because LangChain4j has no HTTP MCP server. It is the counterpart for LangChain4j's client in `mcp-agent`. It serves all three MCP primitives from Open-Meteo, with no API key:
- tools `forecast(city, country?, region?, days)` (1–16 days: WMO code, high/low, rain, wind) and `airQuality(city, country?, region?)` (European AQI, PM2.5, PM10)
- resources `weather://wmo-codes` (the WMO weather code table) and the template `weather://city/{name}` (all places with that exact name)
- prompt `trip-weather-brief(city, startDate, days, country?)`, which renders the trip's forecast into the message and embeds the code table, so the model needs no tool

Both tools look the city up first. A place at least 10× more populous than the next one with the same name wins (Lviv the city, not two villages). Otherwise the tool returns the candidates as an error result, Claude asks the user, and the next request names the place.

The server is **stateless** (`HttpServletStatelessServerTransport`): every `POST /mcp` stands alone, no `Mcp-Session-Id` is issued, and `GET /mcp` returns 405. That lets it scale out and survive restarts, but it can't send anything to a client on its own: no logs or progress during a call, no resource-updated or list-changed notifications, no elicitation or sampling. The capabilities say so (`listChanged`/`subscribe` false). Those features are built in `spring-ai-examples`' MCP modules instead. It listens on all interfaces with no authentication, which is fine for public weather data. An `Origin` check blocks browsers (DNS rebinding); `ai.mcp.allowed-origins` can allow some. Tool arguments are validated against the input schema by the SDK before a handler runs.

`LangChain4jClientOverHttpTest` boots the server on a random port with Open-Meteo mocked and drives it with LangChain4j's own `DefaultMcpClient` + `StreamableHttpMcpTransport`: no session, every tool, resource and prompt, the error codes, a client that asks for the GET stream, and the `Origin` rejection. No API key needed.

### mcp-agent
The MCP client side, built with `langchain4j-mcp`, connected to two servers. `McpClientConfig` builds one `DefaultMcpClient` per server:
- `api-ninjas`: starts the `mcp-server` jar as a subprocess (`StdioMcpTransport`); a local plug-in that serves only this client
- `open-meteo`: connects to `mcp-http-server` over Streamable HTTP (`StreamableHttpMcpTransport`, `ai.mcp.http-server.url`); a remote service any client can use

One `McpToolProvider` over both clients is wired by the starter into the `McpAssistant` AI Service. Claude sees one flat list (`currentWeather`, `forecast`, `airQuality`), and each call is routed to the server that owns the tool. The tool lists are fetched on the first AI Service call and cached. A server that fails to list is skipped with a warning; one that is down at startup stops the app, so start `mcp-http-server` first. Without `API_NINJAS_API_KEY` only `currentWeather` fails, and Claude can still use the HTTP server's tools.

The provider also presents resources as tools (`list_resources`, `get_resource`), so Claude can read the WMO code table or a city's places itself. LangChain4j's default presenter fails the whole listing when one server rejects `resources/list`, which the stdio server always does. `SkippingResourcesAsToolsPresenter` skips servers that answer "Method not found" and keeps the default otherwise.

`GET /tools` lists both servers' tools with the server key. `GET /resources`, `POST /resources/ask`, `GET /prompts` and `POST /prompts/{name}` call the HTTP server directly: resources are application-controlled and prompts user-controlled, so they reach the model through the app, not the tool loop. MCP errors map to HTTP: method not found → 501, invalid params → 400, resource not found → 404, anything else → 502. Both servers push nothing, so one shared client per server is enough; a stateful server with logs or progress would need one client per user.

Tests need no key: `McpToolProviderTest` (merging two servers, routing, a name clash failing, `toolNameMapper`, `filterToolNames`), `SkippingResourcesAsToolsPresenterTest`, `McpServerGatewayTest`, `QuestionControllerTest`, and `StdioMcpClientTest`, which starts the real stdio jar. Build `mcp-server` first (`./mvnw -pl mcp-server package -DskipTests`); the jar path is `ai.mcp.server.jar`, found from either the repository root or the `mcp-agent` directory. Needs `ANTHROPIC_API_KEY`, a running `mcp-http-server`, and `API_NINJAS_API_KEY` for `currentWeather`.

### agentic-tools
A travel assistant with 15 fake, deterministic tools (`TravelTools`), built with `AiServices.builder(...)` in `AiServicesConfig`, because `@AiService` can't set tool search, guardrails or the round-trip limit. `POST /ask?toolSearch=NONE|SIMPLE|VECTOR` (case-sensitive) runs the same question through one of three assistants:
- `NONE` sends all 15 tool definitions with every request.
- `SIMPLE` uses `SimpleToolSearchStrategy`: the model first sees only `tool_search_tool` and the always-visible `getLocalTime`, searches with keywords, and the found tools join the following requests.
- `VECTOR` uses `VectorToolSearchStrategy`, which matches a natural-language query against the tool descriptions with local MiniLM embeddings.

A search costs one extra model call, so the response reports the number of model calls, every tool call, and the token usage summed over the loop (including Anthropic cache reads, since `cache-tools=true`). The `X-User-Id` header reaches `bookFlight`/`bookHotel` through `InvocationParameters`. It is not in the tool schema, so the model can't change who is booked. Each request is capped at 10 model calls; past that the request fails, and there is no per-tool limit.

`POST /plan` returns a `TripPlan` checked by `TripPlanGuardrail`, which parses the JSON (`JsonExtractorOutputGuardrail`) and checks its content. An invalid plan is reprompted up to twice, then returns HTTP 422. The planner keeps a throwaway chat memory per request, because a reprompt is built from memory.

`POST /ask/restricted` and `POST /ask/stream` take their tools from a `RoleToolProvider` instead: the `X-Role` header (`GUEST` by default, or `MEMBER`) goes into `InvocationParameters`, and a guest is never offered `bookFlight` or `bookHotel`. The headers `X-Role` and `X-User-Id` are demo shortcuts: the caller chooses them, so anyone can claim `MEMBER` or book as another user. A real application takes both from the authenticated principal. That is authorization, where tool search is relevance; a withheld tool also cannot be executed if the model names it. A call to a tool that was not offered is answered with "not available" as a tool result (`hallucinatedToolNameStrategy`), so the model replies instead of the request failing, which is what the default does. `/ask/stream` returns server-sent events from a `TokenStream`: `text`, `tool-call` (fragments of a tool call's arguments as the model writes them), `tool-start`, `tool-end`, then `done` or `error`. When the client disconnects, the streaming model call is cancelled through the `StreamingHandle`. A handle belongs to one model call, so a disconnect noticed during a tool execution cannot stop the next call from starting; the stream remembers the client is gone and cancels that call at its first chunk (text or partial tool call). The server only learns of a disconnect from a failed write, which is why tool-call fragments are sent as events: a tool-call round would otherwise write nothing until it had finished.

`POST /ask/skills` adds agent skills (`langchain4j-skills`) on top of all 15 direct tools. Skills are `SKILL.md` folders under `src/main/resources/skills/`, loaded once at startup with `ClassPathSkillLoader`. Only their names and descriptions go into the system message (`Skills.formatAvailableSkills()`). The model loads a skill's instructions with `activate_skill` and its files with `read_skill_resource`. `travel-insurance` unlocks `quoteInsurance` and `buyInsurance` (`InsuranceTools`), which are absent from the tool list until the model activates the skill; its tier details live in a `coverage.md` resource. `packing-list` is instructions only: it adds no tools and fixes the answer's format. A skill can't run scripts (`scripts/` is skipped) and can't declare tools in `SKILL.md`; tools are attached in Java (`SkillsConfig`). Activation lives in the message list and this assistant has no chat memory, so each request starts with no skill active and pays one model call per activation. The response lists the activated skills next to the tool calls.

`ToolSearchTest`, `TripPlannerTest`, `RoleToolProviderTest`, `SkillsTest`, `TravelStreamEventsTest` and `StreamEndpointTest` (the event stream read over HTTP from the embedded server) show what each mode sends, how a reprompt is assembled, what each role is offered, what a skill unlocks and how events and cancellation behave, against stub models with no API key needed. Needs `ANTHROPIC_API_KEY`.

### observability
A café assistant (`CafeAssistant`) that answers from `knowledge/cafe.md` (in-memory store, local MiniLM, top 3 chunks) and calls one tool (`getTodaysSpecial`). `POST /ask` returns the answer, the retrieved chunks with their scores, and the request's `traceId`. Every request becomes one trace in Grafana (Tempo), and the metrics go to Prometheus. Spring Boot starts the `grafana/otel-lgtm` container through Docker Compose and sends both over OTLP.

LangChain4j ships one ready-made Micrometer listener, `ObservationChatModelListener`: a `chat <model>` span per model call plus the `gen_ai.client.token.usage` metric. The rest of the trace comes from this module's own listeners on LangChain4j's hooks:
- `AiServiceObservations`: an `ai_service CafeAssistant.ask` parent span (AI Service events) and an `execute_tool <name>` span per tool call (before/after tool hooks)
- `RetrievalObservationListener`: a `retrieve` span (`ContentRetrieverListener`), with the file, chunk index and score of each result

```
http post /ask
└─ ai_service CafeAssistant.ask
   ├─ retrieve
   ├─ chat claude-sonnet-5-5
   ├─ execute_tool getTodaysSpecial
   └─ chat claude-sonnet-5-5
```

Each span also produces a duration metric (`langchain4j.ai_service`, `langchain4j.retrieval`, `langchain4j.tool`, `gen_ai.client.operation.duration`), visible at `/actuator/metrics`. `ai.observability.include-content=true` puts the question, answer, retrieval query and tool arguments/results on the spans. That's useful for learning, but it's user data, so keep it off in production. No LLM-as-judge evaluation: LangChain4j has no evaluator API.

`ObservationTreeTest` checks the span tree, tags and failure paths against a stub model, with no API key needed. Needs `ANTHROPIC_API_KEY` and Docker. Grafana runs on port 3000, so stop any other LGTM container first.

## Usage

Each module can be run independently:

```
cd <module-name>
../mvnw spring-boot:run
```

## API Examples

### basics (port 8080)

```bash
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "Give me a dad joke"}'
curl -X POST http://localhost:8080/ask/result -H 'Content-Type: application/json' -d '{"question": "Give me a dad joke"}'
curl 'http://localhost:8080/capital?country=France'
curl 'http://localhost:8080/capital/details?country=France'
curl 'http://localhost:8080/capitals?region=Scandinavia'
```

### functions (port 8080)

```bash
curl -X POST http://localhost:8080/weather -H 'Content-Type: application/json' -d '{"question": "What is the weather in Lviv, Ukraine? When are sunrise and sunset?"}'
curl -X POST http://localhost:8080/weather/tool-calls -H 'Content-Type: application/json' -d '{"question": "Compare the current weather in Lviv, Ukraine and Kyiv, Ukraine."}'
```

### rag (port 8080)

```bash
curl -X POST http://localhost:8080/ask/augmented -H 'Content-Type: application/json' -d '{"question": "What is a good truck to pull a Sportsman 232 boat?"}'
curl -X POST http://localhost:8080/ask/routed -H 'Content-Type: application/json' -d '{"question": "What is a good truck to pull a Sportsman 232 boat?"}'
curl -X POST http://localhost:8080/ask/routed -H 'Content-Type: application/json' -d '{"question": "How much can the Chevy Colorado tow?"}'
```

With Milvus (`prod` profile):

```bash
cd rag && docker compose up -d && ../mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

### chat-memory (port 8080)

```bash
curl -X POST http://localhost:8080/chat/42 -H 'Content-Type: application/json' -d '{"question": "Hi, my name is Nazar and I live in Lviv."}'
curl -N -X POST http://localhost:8080/chat/42/stream -H 'Content-Type: application/json' -d '{"question": "What is my name?"}'
curl http://localhost:8080/chat/42
curl -X DELETE http://localhost:8080/chat/42
```

### image (port 8080)

```bash
curl -X POST http://localhost:8080/image -H 'Content-Type: application/json' -d '{"question": "A pickup truck towing a boat along a coastal road at sunset"}' -o generated.png
curl -X POST http://localhost:8080/vision -F 'file=@image/src/test/resources/sample.png;type=image/png'
```

### audio (port 8080)

```bash
curl -X POST http://localhost:8080/audio -H 'Content-Type: application/json' -d '{"question": "Welcome aboard. Today we are towing a boat to the coast."}' -o speech.mp3
```

### mcp-http-server (port 8090) + mcp-agent (port 8080)

Build `mcp-server`, start `mcp-http-server`, then start `mcp-agent` (it starts `mcp-server` itself):

```bash
./mvnw -pl mcp-server,mcp-http-server package -DskipTests
java -jar mcp-http-server/target/mcp-http-server-1.0.0-SNAPSHOT.jar
```

```bash
cd mcp-agent && ../mvnw spring-boot:run
```

```bash
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "What is the weather in Lviv, Ukraine? When are sunrise and sunset?"}'
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "Will it rain in Lviv in the next three days, and how is the air there right now?"}'
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "What is the air quality in Springfield?"}'
curl http://localhost:8080/tools
curl http://localhost:8080/resources
curl -X POST http://localhost:8080/resources/ask -H 'Content-Type: application/json' -d '{"uri": "weather://wmo-codes", "question": "Which codes mean snow?"}'
curl -X POST http://localhost:8080/resources/ask -H 'Content-Type: application/json' -d '{"uri": "weather://city/San%20Jose", "question": "Which San Jose has the most people, and in which time zone is it?"}'
curl http://localhost:8080/prompts
curl -X POST http://localhost:8080/prompts/trip-weather-brief -H 'Content-Type: application/json' -d "{\"city\": \"Lviv\", \"startDate\": \"$(date -d tomorrow +%F)\", \"days\": \"3\"}"
```

The server can be called without the agent. The `Accept` header must list both types:

```bash
curl -X POST http://localhost:8090/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' -d '{"jsonrpc": "2.0", "id": 1, "method": "tools/call", "params": {"name": "forecast", "arguments": {"city": "Lviv", "days": 2}}}'
```

### agentic-tools (port 8080)

```bash
curl -X POST 'http://localhost:8080/ask?toolSearch=NONE' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST 'http://localhost:8080/ask?toolSearch=SIMPLE' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST 'http://localhost:8080/ask?toolSearch=VECTOR' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST http://localhost:8080/plan -H 'Content-Type: application/json' -d '{"question": "3 days in Lisbon from 2026-10-20, flying from Kyiv, mid-range budget."}'
curl -X POST http://localhost:8080/ask/restricted -H 'Content-Type: application/json' -H 'X-User-Id: alice' -H 'X-Role: GUEST' -d '{"question": "Book the morning flight from Kyiv to Lisbon on 2026-10-20."}'
curl -X POST http://localhost:8080/ask/skills -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "I am 34 and going to Portugal for 10 days. Which travel insurance should I get?"}'
curl -N -X POST http://localhost:8080/ask/stream -H 'Content-Type: application/json' -H 'X-User-Id: alice' -H 'X-Role: MEMBER' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
```

### observability (port 8080, Grafana on 3000)

```bash
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "What is today'"'"'s special?"}'
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "Until what time is breakfast served, and how much is a flat white?"}'
curl 'http://localhost:8080/actuator/metrics/gen_ai.client.token.usage?tag=gen_ai.token.type:input'
```

Open http://localhost:3000 → Explore → Tempo and search for the `traceId` from the response.
