# LangChain4j Examples

Examples of using [LangChain4j](https://docs.langchain4j.dev) with Spring Boot, built as a side-by-side counterpart of [spring-ai-examples](../spring-ai-examples): same modules and endpoints, so the two frameworks can be compared directly. See [COMPARISON.md](COMPARISON.md).

## Prerequisites

- Java 25
- Maven (wrapper included)
- Anthropic API key (Claude) — all chat features
- OpenAI API key — only for image generation (`image`) and text-to-speech (`audio`)
- API Ninjas key — only for `functions` and `mcp-server`
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
