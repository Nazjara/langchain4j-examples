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
