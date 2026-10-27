package com.nazjara.configuration;

import com.nazjara.guardrail.TripPlanGuardrail;
import com.nazjara.service.StreamingTravelAssistant;
import com.nazjara.service.TravelAssistant;
import com.nazjara.service.TripPlanner;
import com.nazjara.tool.RoleToolProvider;
import com.nazjara.tool.TravelTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.search.simple.SimpleToolSearchStrategy;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the module's AI Services with {@link AiServices#builder(Class)}, because {@code @AiService}
 * has no attribute for a tool search strategy, guardrails or the round-trip limit. The starter still
 * provides the Anthropic {@link ChatModel} (with {@code cache-tools=true}).
 *
 * <p><b>Three travel assistants</b> share the same {@link TravelTools} and differ only in how the
 * tools reach the model:
 * <ul>
 *   <li>{@code allToolsAssistant}: every request carries all 15 tool specifications.</li>
 *   <li>{@code simpleSearchAssistant}: {@link SimpleToolSearchStrategy}. The model first sees only
 *       {@code getLocalTime} (always visible) and {@code tool_search_tool(terms)}. When it calls the
 *       search tool, the strategy scores each hidden tool (+2 per term found in the name, +1 per
 *       term found in the description) and returns the top 5. The proxy stores the found names on
 *       the tool result message and adds those tools to every later request of the loop. A search
 *       is one more model round trip.</li>
 *   <li>{@code vectorSearchAssistant}: {@link VectorToolSearchStrategy} over a local all-MiniLM-L6-v2
 *       model. The model sends {@code tool_search_tool(query)} in natural language; the query and
 *       each {@code "name: description"} are embedded (tool embeddings are cached) and the 5 closest
 *       tools are returned. 1.20.2's builder has no {@code minScore} setter, so the top 5 come back
 *       even when they are unrelated.</li>
 * </ul>
 * Both strategies are {@code @Experimental}. Found tools live only in the message list, and these
 * assistants have no chat memory, so every request starts again from the search tool. Spring AI's
 * counterpart is {@code ToolSearchToolCallingAdvisor} with a Lucene index, which tracks found tools
 * per session id.
 *
 * <p>The embedding model is created here instead of as a bean: an {@code EmbeddingModel} bean would
 * make the starter's {@code RagAutoConfiguration} add an embedding store and a content retriever
 * that this module does not use.
 *
 * <p><b>Limit.</b> {@code maxToolCallingRoundTrips} ({@code ai.agentic-tools.max-tool-calling-round-trips})
 * caps model calls per request; past it the proxy
 * throws, so the request fails instead of the model being told to stop. LangChain4j has no per-tool
 * call limit (Spring AI: {@code spring.ai.tools.limits.*}).
 *
 * <p><b>Trip planner.</b> All tools, a {@link TripPlanGuardrail} with up to
 * {@code ai.agentic-tools.plan.max-retries} attempts (counting the original reply), and a
 * {@link MessageWindowChatMemory} of {@code ai.agentic-tools.plan.memory-messages} per memory id. A reprompt is built from chat memory, so without
 * memory the retry would carry only the reprompt text. The window is large enough that tool
 * calls and results cannot push the request out of it.
 *
 * <p><b>Role-aware assistants.</b> {@code restrictedAssistant} (blocking) and {@code streamingAssistant}
 * ({@link StreamingTravelAssistant}, {@code TokenStream}) get their tools from a {@link RoleToolProvider}
 * instead of {@code .tools(travelTools)}, and share a {@code hallucinatedToolNameStrategy}. The
 * default strategy throws, which fails the whole request when the model names a tool that was not
 * offered. {@link #unknownToolReply} returns an ordinary tool result instead, so the model reads
 * "not available" on the next round trip and answers the user. It runs only when no executor is
 * registered for the name, so it never fires for a tool the role may use.
 */
@Configuration
@EnableConfigurationProperties(AgenticToolsProperties.class)
@RequiredArgsConstructor
public class AiServicesConfig {

    private final AgenticToolsProperties properties;

    @Bean
    TravelAssistant allToolsAssistant(ChatModel chatModel, TravelTools travelTools) {
        return travelAssistant(chatModel, travelTools).build();
    }

    @Bean
    TravelAssistant simpleSearchAssistant(ChatModel chatModel, TravelTools travelTools) {
        return travelAssistant(chatModel, travelTools)
                .toolSearchStrategy(new SimpleToolSearchStrategy())
                .build();
    }

    @Bean
    TravelAssistant vectorSearchAssistant(ChatModel chatModel, TravelTools travelTools) {
        return travelAssistant(chatModel, travelTools)
                .toolSearchStrategy(new VectorToolSearchStrategy(new AllMiniLmL6V2EmbeddingModel()))
                .build();
    }

    @Bean
    TripPlanner tripPlanner(ChatModel chatModel, TravelTools travelTools) {
        return AiServices.builder(TripPlanner.class)
                .chatModel(chatModel)
                .tools(travelTools)
                .maxToolCallingRoundTrips(properties.maxToolCallingRoundTrips())
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(properties.plan().memoryMessages())
                        .build())
                .outputGuardrails(new TripPlanGuardrail())
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(properties.plan().maxRetries()).build())
                .build();
    }

    @Bean
    TravelAssistant restrictedAssistant(ChatModel chatModel, RoleToolProvider roleToolProvider) {
        return roleAware(AiServices.builder(TravelAssistant.class).chatModel(chatModel), roleToolProvider).build();
    }

    @Bean
    StreamingTravelAssistant streamingAssistant(StreamingChatModel streamingChatModel, RoleToolProvider roleToolProvider) {
        return roleAware(AiServices.builder(StreamingTravelAssistant.class).streamingChatModel(streamingChatModel),
                roleToolProvider).build();
    }

    static ToolExecutionResultMessage unknownToolReply(ToolExecutionRequest request) {
        return ToolExecutionResultMessage.from(request,
                "Tool '%s' is not available. Use only the tools you were given; if none fits, tell the user you can't do that here."
                        .formatted(request.name()));
    }

    private AiServices<TravelAssistant> travelAssistant(ChatModel chatModel, TravelTools travelTools) {
        return AiServices.builder(TravelAssistant.class)
                .chatModel(chatModel)
                .tools(travelTools)
                .maxToolCallingRoundTrips(properties.maxToolCallingRoundTrips());
    }

    private <T> AiServices<T> roleAware(AiServices<T> builder, RoleToolProvider roleToolProvider) {
        return builder
                .toolProvider(roleToolProvider)
                .hallucinatedToolNameStrategy(AiServicesConfig::unknownToolReply)
                .maxToolCallingRoundTrips(properties.maxToolCallingRoundTrips());
    }
}
