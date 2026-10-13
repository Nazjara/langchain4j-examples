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
| `prompt-engineering` | `@SpringBootTest` + `ChatClient`; system, messages, options and `.entity(...)` all set per call | Plain JUnit, no Spring context; test-local AI Service interfaces built with `AiServices.create(...)`; few-shot drops to `ChatModel.chat(messages)` | Thinking display and effort are model-level, so each variant is its own model. Effort has no typed setter (`customParameters`) |
| `functions` | `Function<WeatherRequest, WeatherResponse>` wrapped in `FunctionToolCallback`, passed per call with `.tools(...)`; loop run by the auto-registered `ToolCallingAdvisor` | `@Tool` method on a `@Component`, auto-discovered and wired into every `@AiService`; loop runs inside the AI Service proxy; `Result.toolExecutions()` exposes each call | Schema comes from the method parameters (`@P`), not an input record. Round-trip cap and error handlers exist only on `AiServices.builder(...)`, not in the starter |
| `rag` | `SimpleVectorStore` (JSON file) or auto-configured Milvus `VectorStore`; `TikaDocumentReader` → `TokenTextSplitter` → `vectorStore.add`; manual `similaritySearch` → `.st` template → `ChatClient` | `InMemoryEmbeddingStore` (heap only, re-ingested on each start) or hand-built `MilvusV2EmbeddingStore`; `ApacheTikaDocumentParser` → `EmbeddingStoreIngestor` (recursive splitter + `EmbeddingModel` + store); manual path plus an `@AiService` with the auto-configured `ContentRetriever` | The store and the embedding model are separate objects. Declarative RAG is a proxy slot (`DefaultRetrievalAugmentor`), where Spring AI uses an advisor |
| `chat-memory` | `MessageWindowChatMemory` over `JdbcChatMemoryRepository` (one row per message) applied by `MessageChatMemoryAdvisor`; conversation id as an advisor param; `VectorStoreChatMemoryAdvisor` over pgvector; `.stream().content()` | `ChatMemoryProvider` bean → `MessageWindowChatMemory` per `@MemoryId`, over `SQLChatMemoryStore` (community, one JSON row per conversation); `Flux<String>` return type via `langchain4j-reactor` | The id is a typed method parameter. No vector memory. History and clear go through the store, because `ChatMemoryAccess` only sees the proxy's cache |
| `image` | `OpenAiImageModel.call(new ImagePrompt(text, options))` with DALL·E 3 and per-call `OpenAiImageOptions`; vision through a `Media` attached to a hand-built `UserMessage` passed to `ChatClient`; unused OpenAI models switched off with `spring.ai.model.*=none` | `ImageModel.generate(String)` on the starter's `OpenAiImageModel` (`gpt-image-1`, options fixed in `langchain4j.open-ai.image-model.*`); vision through an `ImageContent` parameter on an `@AiService` method | Each OpenAI bean needs its own `api-key`, so nothing has to be switched off. The Anthropic and OpenAI starters clash over the HTTP client until the JDK client is excluded |
| `audio` | Auto-configured `TextToSpeechModel`; voice, speed, format and instructions per call in `OpenAiAudioSpeechOptions` | Hand-built `OpenAiTextToSpeechModel` (`@Experimental`, no starter properties); `synthesize(text)` sends model, input and voice only | Always MP3 at the default speed, with no speaking instructions |
| `mcp-server` | `spring-ai-starter-mcp-server-webmvc`: `@McpTool`, `@McpResource` and `@McpPrompt` beans published over Streamable HTTP on a port, configured by `spring.ai.mcp.server.*`; tools can log back to the client through `McpSyncRequestContext` | `langchain4j-community-mcp-server`: `new McpServer(toolBeans)` + `new StdioMcpServerTransport(server)`, built by hand; plain `@Tool` methods, title and hints through `@Tool(metadata)` | **Tools only, stdio only.** No resources, prompts, HTTP transport, notifications or `ping`. One server process per client |
| `mcp-agent` | `spring-ai-starter-mcp-client`: connections in properties, auto-configured `ToolCallbackProvider` passed to `ChatClient`; `McpSyncClient` for resources and prompts, prompt messages converted by hand | `langchain4j-mcp`: hand-built `StdioMcpTransport` → `DefaultMcpClient` → `McpToolProvider`, auto-wired into `@AiService`; `readResource` / `getPrompt` on `McpClient`, `McpPromptMessage.toChatMessage()` | Client is complete (tools, resources, prompts; stdio, Streamable HTTP, WebSocket) but has no starter. Its resource and prompt calls cannot be run against LangChain4j's own server |

### mcp-server + mcp-agent: what felt different

- **The server is the framework's weakest part so far.** `langchain4j-community-mcp-server` is three classes. It answers `initialize`, `tools/list` and `tools/call`; every other method gets JSON-RPC error `-32601`. There is no API to register a resource or a prompt, no HTTP transport, no log or progress notifications, and no `ping`. This is unchanged in 1.22.0, whose only addition is a Boot starter around the same stdio server. Spring AI's server starter publishes tools, resources and prompts over Streamable HTTP, SSE or stdio.
- **The two halves don't meet.** LangChain4j's client implements all three MCP primitives, and its server implements one. `mcp-agent` has endpoints for resources and prompts because the client API exists, but against `mcp-server` they return 501 ("Method not found: resources/read"). They are only proven against a stubbed `McpClient` (`McpServerGatewayTest`, `QuestionControllerTest`). In Spring AI the same two modules exercise all three end to end.
- **Same machine only.** Stdio means the agent starts the server jar as a subprocess, so the server must sit on the agent's machine, has no port, serves exactly one client and dies with it. It cannot be deployed as a shared service. For a Java application that owns both sides this is little more than a `@Tool` library with a process boundary; its real use is as a plug-in for hosts you don't control (Claude Desktop, Claude Code, an IDE). Its stdout is the protocol channel: the banner is off and Logback is redirected to stderr, which the client forwards to its own log at DEBUG as `[STDERR] ...`. Spring AI's module is a normal web app on :8090 that any number of clients can share.
- **No MCP-specific tool annotation.** The server takes any object with `@Tool` methods, the same bean an AI Service would use. `@Tool(metadata = "{...}")` carries the MCP extras: `title` becomes the tool title and the `*Hint` keys become `annotations`. Spring AI has typed `@McpTool.McpAnnotations`.
- **Client beans are hand-built.** There is no MCP client starter: transport, client and `McpToolProvider` are created in `@Configuration`. After that the starter does help, because `@AiService` picks up the `ToolProvider` bean on its own. Spring AI needs only `spring.ai.mcp.client.streamable-http.connections.<name>.url`.
- **The tool list is lazy.** `DefaultMcpClient.build()` runs the handshake only. `tools/list` is sent on the first AI Service call, when the proxy asks the `ToolProvider` for tools, and the result is cached until the server announces a change (observed: no `tools/list` during startup, one after two `/ask` calls). The provider is asked on every call and receives the user message, so tools can differ per request. Spring AI fetches the list at startup.
- **To the model, an MCP tool is just a tool.** The Anthropic request lists `currentWeather` with the same `input_schema` as in `functions`. The MCP title and hints are not sent to the model; they stay in `ToolSpecification.metadata()` for the application.
- **The client is richer than Spring AI's on a few points.** `McpPromptMessage.toChatMessage()` converts prompt results (Spring AI's module maps them by hand), `McpToolProvider` can filter and rename tools and present resources as tools, and `McpClientListener` has a hook around every request.
- **The client tries a newer protocol first.** It opens with `server/discover` (protocol `2026-07-28`), gets "Method not found" from the server, and falls back to `initialize` with `2025-11-25`. The server simply echoes whatever version the client asked for.
- **Health checks pass for the wrong reason.** The client pings every 30 seconds by default. The server rejects `ping` as an unknown method, but the client only waits for a reply and does not read it, so the error counts as alive (`StdioMcpClientTest`).
- **Tool failures are results, not errors.** An exception in the tool comes back as a `tools/call` result with `isError: true` and the exception message as text, the same as an unknown tool name. Only an unknown *method* is a JSON-RPC error.

### image: what felt different

- **Vision stays declarative.** `String describe(@UserMessage ImageContent image)` with a method-level `@UserMessage` template is the whole feature. The proxy builds one user message with the template text first and the image second (asserted in `QuestionControllerTest`). Spring AI needs a hand-built `UserMessage` with a `Media` and drops to `.messages(...)`.
- **You encode the image yourself.** `Image` holds a URL or a base64 string and has no `byte[]` field, so the controller base64-encodes the upload. Spring AI's `Media` accepts a `Resource` and encodes it internally. The request on the wire is the same: an `image` block with a `base64` source.
- **Image options belong to the bean.** `ImageModel.generate` takes only the prompt, so model, size, quality and output format come from `langchain4j.open-ai.image-model.*` and one bean means one configuration. Spring AI passes `OpenAiImageOptions` per call.
- **No `style` or `response_format`.** LangChain4j 1.20.2 sends `model`, `prompt`, `n`, `size`, `quality`, `background`, `output_format`, `output_compression`, `moderation` and `user`. The DALL·E constants in `OpenAiImageModelName` are deprecated, so the module uses `gpt-image-1`, which always answers with base64. Spring AI's module used `dall-e-3` with `quality=hd` and `style=natural`.
- **OpenAI beans are opt-in one by one.** The starter creates a model only when that model's own `api-key` property is set, so setting `image-model.api-key` adds no OpenAI `ChatModel` next to Claude. Spring AI enables every model of a starter by default and the module turns the others off with `spring.ai.model.*=none`.
- **Two provider starters don't start together out of the box.** The OpenAI starter brings LangChain4j's Spring RestClient HTTP client and passes it to its own models. The Anthropic starter passes none, finds both that client and the JDK client on the classpath, and fails with "Conflict: multiple HTTP clients". The module's pom excludes `langchain4j-http-client-jdk`; the alternative is the `langchain4j.http.clientBuilderFactory` system property. Spring AI's two starters start together with no extra configuration.
- **No usage on images.** `Response<Image>` carries no token usage in 1.20.2, and `revisedPrompt` is only filled when the API returns one.

### audio: what felt different

- **No starter support.** The OpenAI starter has no text-to-speech properties, so the model is built in `@Configuration` from `ai.openai.*`. Spring AI auto-configures it from the API key.
- **Three request fields.** `OpenAiTextToSpeechModel` sends `model`, `input` and `voice`. `TextToSpeechRequest` can override the voice per call and nothing else. Speed, speaking instructions and the audio format are missing, so the Spring AI module's `speed(1.0)`, `responseFormat(MP3)` and `instructions("Calm, friendly narrator")` have no counterpart; the reply is always labelled `audio/mpeg`.
- **Experimental API.** `TextToSpeechModel` and its request/response types are marked `@Experimental`, and input is capped at 4096 characters on the client side.
- **Bytes, not base64.** OpenAI returns the audio as the HTTP body, so `Audio` exposes `binaryData()`. `Image` has no such field because images arrive inside JSON.

### chat-memory: what felt different

- **The conversation id is in the signature.** `chat(@MemoryId String id, @UserMessage String message)` can't be called without an id. Spring AI passes it as an untyped advisor parameter, and forgetting it silently falls back to a default conversation.
- **Memory is a provider, not an advisor.** The proxy asks the `ChatMemoryProvider` once per id and caches the `ChatMemory`. That object holds no messages: every read goes to the `ChatMemoryStore`, so deleting from the store is enough to reset a conversation.
- **The store keeps the window, not the log.** `SQLChatMemoryStore` upserts the whole message list as one JSON row per conversation, so evicted messages disappear from the database too. Spring AI's `JdbcChatMemoryRepository` uses one row per message, but its window rewrites the table the same way.
- **The JDBC store is a community module.** It needs `langchain4j-community-sql` and the community BOM (`1.20.0-beta30`), and is built by hand from Boot's `DataSource`. Spring AI's `spring-ai-starter-model-chat-memory-repository-jdbc` auto-configures and initializes the schema.
- **`ChatMemoryAccess` is cache-only (1.20.2).** An AI Service that extends it gets `getChatMemory(id)` and `evictChatMemory(id)`, but eviction only drops the cached object without clearing the store, and `getChatMemory` returns `null` for ids not used since startup. The module's history and clear endpoints call the store instead.
- **A failed turn leaves its question behind.** The user message is written to memory before the model is called, so a call that fails (a 401 in the smoke test) leaves a stored `USER` message with no answer. The next turn sends two user messages in a row.
- **Streaming is a return type.** Declaring `Flux<String>` switches the proxy to the `StreamingChatModel` bean, a separate bean with its own `langchain4j.anthropic.streaming-chat-model.*` properties. The answer is stored when the model's stream completes. Spring AI streams from the same `ChatClient` with `.stream()`.
- **Cancelling the `Flux` doesn't cancel the model call (1.20.2).** `langchain4j-reactor`'s `TokenStreamToFluxAdapter` only uses `onPartialResponse(String)` and never calls `StreamingHandle.cancel()`. When the client disconnects, Anthropic keeps generating (and billing) to the end, and the full reply is still stored in memory (observed live; proven in `MemoryWindowTest`). The cancellation API does exist: `TokenStream.onPartialResponseWithContext` exposes the handle.
- **Observed run** (Sonnet 5.5):
  - Turn 2 resent the whole window: `system` as a separate field, then user, assistant, user in `messages`. Input grew from 60 to 137 tokens. `cache_read_input_tokens` was 0 on every call, and no request carried `cache_control`: LangChain4j 1.20.2 can mark only system messages and tools.
  - Streaming: response headers arrived 0.78 s after the request, the first text delta 0.8 s after it, and the last one 2.2 s after it (141 output tokens, 0 thinking). Deltas were 1–30 characters, often starting with a space.
  - Conversation 7 got only its own message and answered "I don't know your name yet". It was the only call that used adaptive thinking (39 tokens, empty thinking text with a signature).
- **Vector memory is skipped.** LangChain4j has no counterpart of `VectorStoreChatMemoryAdvisor`, which recalls semantically similar past messages instead of the most recent ones.

### basics: what felt different

- **No implementation class.** The interface *is* the service, so template loading, parameter binding and response parsing all move out of your code and into annotations.
- **Generics just work.** `List<CapitalDetails>` is read from the method signature, so no type token is needed.
- **Native structured output is opt-in and awkward to enable.** It only switches on when the model reports `RESPONSE_FORMAT_JSON_SCHEMA` in `supportedCapabilities()`. The Anthropic starter has no property for that, and its `ChatModel` bean has no `@ConditionalOnMissingBean`, so you have to build the model yourself under non-starter property keys. Once enabled, the schema goes into Anthropic's `output_config.format`, and the user message is exactly the template text.
- **Lists are wrapped.** Anthropic needs an object at the root of the schema, so `List<T>` is sent as `{"values": [...]}` and unwrapped by the proxy.
- **Record fields are not `required`.** The generated item schema has `"required": []`, so the model is allowed to leave fields out.
- **Metadata without leaving the high-level API.** Changing the return type to `Result<String>` adds token usage and finish reason. Spring AI gets these from `.call().chatResponse()`.

### prompt-engineering: what felt different

- **No container needed.** Models and AI Services are plain objects, so the tests build them directly. Spring AI's tests need `@SpringBootTest` to get a `ChatClient.Builder`.
- **The prompt moves into the declaration.** System prompts and templates sit on the interface (`@SystemMessage`, `@UserMessage(fromResource)`), not in the call chain. That suits fixed prompts, and is clumsy for one-off experiments.
- **Few-shot pairs don't fit an AI Service.** Annotations describe one system and one user message, with no way to declare example `AiMessage` turns, so `FewShotTest` calls `ChatModel.chat(messages)`. Spring AI keeps it in the fluent API with `.messages(...)`.
- **Options are split between model and request.** Caching and thinking type can be set per request (`AnthropicChatRequestParameters`), but `thinkingDisplay` and `customParameters` exist only on the model builder. An AI Service method can't pass per-request parameters anyway, so every variant (thinking on, effort low/high) is a separately built model. Spring AI sets everything per call on `AnthropicChatOptions`.
- **Effort is untyped.** LangChain4j 1.20.2 has no `effort` setter; it goes through `customParameters(Map.of("output_config", Map.of("effort", ...)))`, which is merged into the request body as-is.
- **Native structured output is a model capability.** Spring AI opts in per call (`spec.useProviderStructuredOutput()`); LangChain4j opts in once on the model (`supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)`).
- **Thinking is a field, not a generation.** LangChain4j puts the summary on `AiMessage.thinking()` (needs `returnThinking(true)`); Spring AI returns each thinking block as a separate generation.
- **Anthropic counters need a cast.** Cache read/write tokens live on `AnthropicTokenUsage`, and the cache-miss reason on `AnthropicChatResponseMetadata.cacheDiagnostics()`. Spring AI's generic `Usage` exposes the read/write counts directly, but has no miss reason.
- **Same prompt, no visible reasoning.** With the shared 3x8 domino problem, Sonnet 5.5 returned no thinking text and high effort used fewer output tokens than low (116 vs 189). The answer is well known, so adaptive thinking likely skipped reasoning altogether. This is a property of the prompt, not of either framework.

### functions: what felt different

- **Tools are global by default.** The starter hands every `@Tool` bean to every `@AiService` in `AUTOMATIC` wiring mode. Spring AI attaches tools per call, so they are scoped by default. In LangChain4j, scoping needs `wiringMode = EXPLICIT` + `tools = {"beanName"}`.
- **Parameters instead of an input record.** The JSON schema is built from the method's parameters and their `@P` descriptions, so the `WeatherRequest` record from Spring AI goes away.
- **The loop is inspectable.** `Result<T>.toolExecutions()` returns every call (request arguments, result, failure flag), and `intermediateResponses()` returns each model reply in between. Spring AI's advisor runs the loop and returns only the final response.
- **Loop controls aren't in the starter.** `maxToolCallingRoundTrips` (default 100; `maxSequentialToolsInvocations` is deprecated in 1.20.2), `toolExecutionErrorHandler`, `toolArgumentsErrorHandler` and `hallucinatedToolNameStrategy` are builder-only. `@AiService` can't set them, so a module that needs them builds the service in `@Configuration`.
- **Tool failures go to the model by default.** In synchronous AI Services, an exception thrown by the tool is sent to the model as the tool result, with a WARN log. Argument-parsing errors are rethrown instead. Async and reactive services use the opposite defaults.
- **Observed run** (Sonnet 5.5, API Ninjas temporarily stubbed because the free plan rejects `city`/`country`):
  - One question costs two model calls. The first replies `stop_reason: tool_use` with no thinking (0 thinking tokens). The second resends the history plus the `tool_result` and answers after thinking.
  - Lviv: 553 + 725 input tokens, 77 + 543 output tokens.
  - "Compare Lviv and Kyiv" came back as **parallel tool use**: two `tool_use` blocks in one reply. LangChain4j ran both and sent both `tool_result`s in a single message, so it was still two calls (556 + 948 input, 152 + 1117 output).
  - Local sunrise and sunset were converted correctly (EEST, UTC+3).
  - No `thinking`-block 400: the tool-call replies carried no thinking, so there was nothing to resend.
- **The programmatic form is the same contract.** `ToolSpecification` (name, description, `JsonObjectSchema`) + `ToolExecutor` (raw JSON arguments in, string out) is LangChain4j's counterpart of `FunctionToolCallback`. `ProgrammaticToolsTest` drives it with a scripted stub `ChatModel`, so the whole loop is tested without an API key.

### rag: what felt different

- **The store doesn't embed.** Spring AI's `VectorStore.add`/`similaritySearch` call the embedding model internally. A LangChain4j `EmbeddingStore` only accepts vectors, so the `EmbeddingModel` is passed to the ingestor and the retriever separately, and the manual path embeds the question itself.
- **The in-memory store is not saved to a file (deliberate difference).** Spring AI's module saves `SimpleVectorStore` to a JSON file and reloads it at startup, which makes an in-memory store look persistent. Here the store lives only on the heap and is re-ingested on every start (a few seconds), so Milvus is the only store that persists. `InMemoryEmbeddingStore.serializeToFile`/`fromFile` would offer the same snapshot trick.
- **Two types instead of one.** A `Document` is the parsed file and a `TextSegment` is an embeddable chunk. Spring AI uses `Document` for both.
- **RAG with zero wiring.** Once `EmbeddingModel` and `EmbeddingStore` beans exist, the starter's `RagAutoConfiguration` creates an `EmbeddingStoreContentRetriever` (`langchain4j.rag.retrieval.max-results`/`min-score`), and every `@AiService` picks it up. Spring AI needs a `QuestionAnswerAdvisor` added to the `ChatClient`.
- **Different injected prompt.** The augmented path appends segments to the user message under LangChain4j's default "Answer using the following information:" (`DefaultContentInjector`), not the module's template. Changing it means building a `DefaultRetrievalAugmentor` with a custom `ContentInjector`, which the starter can't do.
- **The embedding model ships in a jar.** `langchain4j-embeddings-all-minilm-l6-v2` bundles the ONNX model and tokenizer. Spring AI's transformers starter downloads them on first use.
- **Token-sized splitting needs an explicit estimator.** `DocumentSplitters.recursive(256, 32, new HuggingFaceTokenCountEstimator())` measures in MiniLM tokens. Without an estimator, the sizes are characters.
- **Milvus has no Boot starter.** The store is built in `@Configuration` from custom `ai.rag.milvus.*` properties. The v1 `MilvusEmbeddingStore` is deprecated in favour of `langchain4j-milvus-v2`'s `MilvusV2EmbeddingStore`. The collection is `langchain4j_minilm`, because the two libraries use different field layouts.

## Gaps and strengths

### Only / better in Spring AI

- Per-call Anthropic options: thinking display and effort, with a typed `effort` (prompt-engineering)
- Few-shot message pairs inside the high-level API (prompt-engineering)
- Tools scoped per call by default (functions)
- Milvus auto-configured from properties (rag)
- Vector chat memory (`VectorStoreChatMemoryAdvisor`); JDBC chat memory auto-configured with schema init (chat-memory)
- Per-call image options; provider starters that start together without HTTP-client exclusions (image)
- Text-to-speech auto-configured, with speed, format and speaking instructions (audio)
- MCP server with resources, prompts, HTTP transports and notifications; LangChain4j's serves tools over stdio only (mcp-server)
- MCP client and server configured from properties by starters (mcp-server, mcp-agent)

### Only / better in LangChain4j

- Cache-miss diagnostics (`returnCacheDiagnostics`, `cacheMissReasonType()`) (prompt-engineering)
- Models and AI Services usable without a Spring context (prompt-engineering)
- Tool calls returned with the answer (`Result.toolExecutions()`), plus pluggable tool-error handlers (functions)
- Declarative RAG auto-wired from two beans; embedding model bundled in a jar (rag)
- Conversation id as a typed `@MemoryId` parameter (chat-memory)
- Images as a typed AI Service parameter; OpenAI models enabled one by one, so no switching off (image)
- MCP prompt results converted to chat messages by the library; tool filtering and renaming on the provider; per-request tool lists (mcp-agent)

## Setup notes

- This repo uses `claude-sonnet-5-5` as its minimum model, while spring-ai-examples `basics` uses Haiku 4.5. Token counts are therefore not directly comparable.
- LangChain4j's Boot 4 starters are built against Spring Boot 4.0.5; with the 4.1.1 parent, Boot's dependency management wins (all `org.springframework.boot` artifacts resolve to 4.1.1).
