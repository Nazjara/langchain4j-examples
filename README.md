# LangChain4j Examples

Examples of using [LangChain4j](https://docs.langchain4j.dev) with Spring Boot, built as a side-by-side counterpart of [spring-ai-examples](../spring-ai-examples): same modules, endpoints and models, so the two frameworks can be compared directly. See [COMPARISON.md](COMPARISON.md).

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

## Usage

Each module can be run independently:

```
cd <module-name>
../mvnw spring-boot:run
```

## API Examples
