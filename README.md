# LangChain4j Examples

Examples of using [LangChain4j](https://docs.langchain4j.dev) with Spring Boot, built as a side-by-side counterpart of [spring-ai-examples](../spring-ai-examples): same modules and endpoints, so the two frameworks can be compared directly. See [COMPARISON.md](COMPARISON.md).

## Prerequisites

- Java 25
- Maven (wrapper included)
- Anthropic API key (Claude) — all chat features
- OpenAI API key — only for image generation (`image`) and text-to-speech (`audio`)
- API Ninjas key — only for `functions`, `mcp-server` and `mcp-agent`
- Docker — only for the `rag` `prod` profile (Milvus), `chat-memory` (PostgreSQL) and `observability-eval` (Grafana LGTM)

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
Retrieval-augmented generation over the same documents as spring-ai-examples (a tow-vehicle list and four Yamaha boat performance bulletins). On every start, the documents are parsed with Apache Tika, split into segments of about 256 tokens, embedded in-process with all-MiniLM-L6-v2, and kept in an `InMemoryEmbeddingStore` on the heap, which is lost on shutdown. `POST /ask` does RAG by hand (`ManualRagService`: search → template → `ChatModel`). `POST /ask/augmented` is LangChain4j-only: the `RagAssistant` AI Service gets the starter's auto-configured `ContentRetriever`, so retrieval happens inside the proxy. Under the `prod` profile, segments go to Milvus (`docker compose up -d` in `rag/` first). Needs `ANTHROPIC_API_KEY`.

### chat-memory
One conversation per id: `ChatAssistant` takes a `@MemoryId`, and the starter wires in a `ChatMemoryProvider` that builds a 20-message `MessageWindowChatMemory` for each id. Messages persist in PostgreSQL through LangChain4j's `SQLChatMemoryStore` (`langchain4j-community-sql`), one JSON row per conversation. Boot's Docker Compose support starts the database automatically. `POST /chat/{id}` answers in one response. `POST /chat/{id}/stream` returns the same answer as Server-Sent Events through the AI Service's `Flux<String>` method; the reply is stored when the model's stream completes. Closing the connection doesn't stop the Anthropic call in LangChain4j 1.20.2. `GET /chat/{id}` shows the stored window and `DELETE /chat/{id}` clears it. `MemoryWindowTest` shows the window and per-id isolation against a stub model, with no API key or database needed. Needs `ANTHROPIC_API_KEY` and Docker. Boot looks for `compose.yaml` in the working directory, so an IntelliJ run configuration needs its working directory set to `$MODULE_WORKING_DIR$`.

### image
Two kinds of model in one app. `POST /image` generates a picture with OpenAI's `gpt-image-1` through LangChain4j's `ImageModel`, which the OpenAI starter builds from `langchain4j.open-ai.image-model.*`; the image comes back as base64 and is returned as PNG. `POST /vision` takes a multipart upload and asks Claude to describe it: the `VisionAssistant` AI Service has an `ImageContent` parameter, which the proxy sends as an image block next to the prompt text. `QuestionControllerTest` runs both endpoints against stub models and shows the exact user message, with no API key needed. Needs `ANTHROPIC_API_KEY` for `POST /vision` and `OPENAI_API_KEY` for `POST /image` (OpenAI may require a verified organization for `gpt-image-1`); each endpoint works with its own key alone. The module's pom excludes LangChain4j's JDK HTTP client, because the Anthropic and OpenAI starters otherwise fail at startup with "multiple HTTP clients".

### audio
Text-to-speech: `POST /audio` turns text into MP3 with OpenAI's `gpt-4o-mini-tts` through LangChain4j's `TextToSpeechModel`. The OpenAI starter has no text-to-speech properties, so `TextToSpeechModelConfig` builds the model by hand from `ai.openai.*`. Only the model, the text and the voice can be set; speed, speaking instructions and the audio format cannot. Needs `OPENAI_API_KEY` only.

### mcp-server
A Model Context Protocol server built with `langchain4j-community-mcp-server`. It publishes the `currentWeather` tool, declared with the same `@Tool` annotation an AI Service uses; `@Tool(metadata)` adds the MCP title and the read-only/idempotent hints. LangChain4j's server speaks stdio only and serves tools only: it has no HTTP transport, no resources, no prompts and no notifications. The app therefore has no web server and is not run on its own. An MCP client on the same machine starts the jar as a subprocess and talks to it over stdin and stdout; it cannot be reached over the network or shared between clients. Because of that, so the banner is off and all logging goes to stderr. `McpServerTest` drives the protocol handler directly and shows each response, including "Method not found" for resources, prompts and `ping`; no API key needed. Needs `API_NINJAS_API_KEY` at runtime.

### mcp-agent
The MCP client side, built with `langchain4j-mcp`. `McpClientConfig` starts the `mcp-server` jar as a subprocess (`StdioMcpTransport`), connects a `DefaultMcpClient`, and wraps it in an `McpToolProvider` that the starter wires into the `McpAssistant` AI Service. `POST /ask` lets Claude call the remote `currentWeather` tool; `GET /tools` lists what the server publishes. The tool list is fetched on the first AI Service call, not at startup, and cached afterwards. The remaining endpoints show the client's resource and prompt API (`GET /resources`, `POST /resources/ask`, `GET /prompts`, `POST /prompts/{name}`). They return HTTP 501 here, because LangChain4j's own server cannot serve resources or prompts; `McpServerGatewayTest` and `QuestionControllerTest` run them against a stubbed client instead. `StdioMcpClientTest` starts the real server jar and asserts what works and what is rejected. Build `mcp-server` first (`./mvnw -pl mcp-server package -DskipTests`); the jar path is `ai.mcp.server.jar`, found from either the repository root or the `mcp-agent` directory, so it works from IntelliJ and from `../mvnw spring-boot:run` alike. Needs `ANTHROPIC_API_KEY` and `API_NINJAS_API_KEY`.

### agentic-tools
A travel assistant with 15 fake, deterministic tools (`TravelTools`), built with `AiServices.builder(...)` in `AiServicesConfig`, because `@AiService` can't set tool search, guardrails or the round-trip limit. `POST /ask?toolSearch=NONE|SIMPLE|VECTOR` (case-sensitive) runs the same question through one of three assistants:
- `NONE` sends all 15 tool definitions with every request.
- `SIMPLE` uses `SimpleToolSearchStrategy`: the model first sees only `tool_search_tool` and the always-visible `getLocalTime`, searches with keywords, and the found tools join the following requests.
- `VECTOR` uses `VectorToolSearchStrategy`, which matches a natural-language query against the tool descriptions with local MiniLM embeddings.

A search costs one extra model call, so the response reports the number of model calls, every tool call, and the token usage summed over the loop (including Anthropic cache reads, since `cache-tools=true`). The `X-User-Id` header reaches `bookFlight`/`bookHotel` through `InvocationParameters`. It is not in the tool schema, so the model can't change who is booked. Each request is capped at 10 model calls; past that the request fails, and there is no per-tool limit.

`POST /plan` returns a `TripPlan` checked by `TripPlanGuardrail`, which parses the JSON (`JsonExtractorOutputGuardrail`) and checks its content. An invalid plan is reprompted up to twice, then returns HTTP 422. The planner keeps a throwaway chat memory per request, because a reprompt is built from memory.

`ToolSearchTest` and `TripPlannerTest` show what each mode sends and how a reprompt is assembled, against a stub model with no API key needed. Needs `ANTHROPIC_API_KEY`.

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
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "What is a good truck to pull a Sportsman 232 boat?"}'
curl -X POST http://localhost:8080/ask/augmented -H 'Content-Type: application/json' -d '{"question": "What is a good truck to pull a Sportsman 232 boat?"}'
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

### mcp-agent (port 8080)

Starts `mcp-server` itself; build that module first.

```bash
curl -X POST http://localhost:8080/ask -H 'Content-Type: application/json' -d '{"question": "What is the weather in Lviv, Ukraine? When are sunrise and sunset?"}'
curl http://localhost:8080/tools
```

These return 501, because LangChain4j's MCP server has no resources or prompts:

```bash
curl http://localhost:8080/resources
curl -X POST http://localhost:8080/resources/ask -H 'Content-Type: application/json' -d '{"uri": "movies://Avatar", "question": "Who directed this movie?"}'
curl http://localhost:8080/prompts
curl -X POST http://localhost:8080/prompts/weather-report -H 'Content-Type: application/json' -d '{"city": "Lviv", "country": "Ukraine"}'
```

### agentic-tools (port 8080)

```bash
curl -X POST 'http://localhost:8080/ask?toolSearch=NONE' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST 'http://localhost:8080/ask?toolSearch=SIMPLE' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST 'http://localhost:8080/ask?toolSearch=VECTOR' -H 'Content-Type: application/json' -H 'X-User-Id: alice' -d '{"question": "Find me a flight from Kyiv to Lisbon on 2026-10-20 and convert the cheapest price to UAH."}'
curl -X POST http://localhost:8080/plan -H 'Content-Type: application/json' -d '{"question": "3 days in Lisbon from 2026-10-20, flying from Kyiv, mid-range budget."}'
```
