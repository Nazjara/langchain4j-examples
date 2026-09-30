# Spring AI 2.0 vs LangChain4j 1.20

Filled in module by module. Versions compared: Spring AI 2.0.1 and LangChain4j 1.20.2 (starters `1.20.2-beta30`), both on Spring Boot 4.1.1 and Java 25, with Claude as the chat model.

## Mental model

| Concept | Spring AI 2.0 | LangChain4j |
|---|---|---|
| Main abstraction | `ChatClient` (fluent, imperative) + advisor chain | AI Service: a Java interface implemented by a generated proxy (`AiServices.builder(...)` / `@AiService`) |
| Low-level model | `ChatModel` | `ChatModel` / `StreamingChatModel` (`chat(ChatRequest)`) |
| Prompt templates | `.st` StringTemplate, `{var}` | `@SystemMessage` / `@UserMessage`, `{{var}}`, `fromResource` |
| Structured output | `.entity(Type)` | The method's return type |
| Cross-cutting concerns | Advisors (ordered chain) | Fixed slots on the AI Service (memory, retriever/augmentor, tools, tool search, guardrails, listeners) |
| Tools | `@Tool`, `FunctionToolCallback` | `@Tool` / `@P`, `ToolSpecification` + `ToolExecutor`, `ToolProvider` |
| Memory | `ChatMemory` + advisor; conversation id as advisor param | `ChatMemoryProvider` + `@MemoryId`; `ChatMemoryStore` SPI |
| RAG | `VectorStore`, `QuestionAnswerAdvisor` | `EmbeddingStore` + `EmbeddingStoreIngestor`, `ContentRetriever`, `RetrievalAugmentor` |
| Multi-agent | — | `langchain4j-agentic` |
| Observability | Micrometer Observations built in | `ChatModelListener`, AI Service events, `langchain4j-observation` |
| Evaluation | `RelevancyEvaluator`, `FactCheckingEvaluator` | None built in |

## Per module

| Module | Spring AI | LangChain4j | Notes |
|---|---|---|---|
| `basics` | `AiService` interface + hand-written `AiServiceImpl` over `ChatClient`; `.entity(...)` / `ParameterizedTypeReference`; `.st` templates | `@AiService` interface only (generated proxy); return type drives the schema; `@UserMessage(fromResource)` + `@V`; `Result<T>` for metadata | One class fewer. Native JSON-schema output needs a hand-built `ChatModel` (`supportedCapabilities`) because the starter can't declare capabilities |

### basics: what felt different

- **No implementation class.** The interface *is* the service, so template loading, parameter binding and response parsing all move out of your code and into annotations.
- **Generics just work.** `List<CapitalDetails>` is read from the method signature, so no type token is needed.
- **Native structured output is opt-in and awkward to enable.** It only switches on when the model reports `RESPONSE_FORMAT_JSON_SCHEMA` in `supportedCapabilities()`. The Anthropic starter has no property for that, and its `ChatModel` bean has no `@ConditionalOnMissingBean`, so you have to build the model yourself under non-starter property keys. Once enabled, the schema goes into Anthropic's `output_config.format`, and the user message is exactly the template text.
- **Lists are wrapped.** Anthropic needs an object at the root of the schema, so `List<T>` is sent as `{"values": [...]}` and unwrapped by the proxy.
- **Record fields are not `required`.** The generated item schema has `"required": []`, so the model is allowed to leave fields out.
- **Metadata without leaving the high-level API.** Changing the return type to `Result<String>` adds token usage and finish reason. Spring AI gets these from `.call().chatResponse()`.

## Gaps and strengths

### Only / better in Spring AI

### Only / better in LangChain4j

## Setup notes

- This repo uses `claude-sonnet-5-5` as its minimum model, while spring-ai-examples `basics` uses Haiku 4.5. Token counts are therefore not directly comparable.
- LangChain4j's Boot 4 starters are built against Spring Boot 4.0.5; with the 4.1.1 parent, Boot's dependency management wins (all `org.springframework.boot` artifacts resolve to 4.1.1).
