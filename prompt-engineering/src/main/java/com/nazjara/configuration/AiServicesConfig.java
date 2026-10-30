package com.nazjara.configuration;

import com.nazjara.model.Audience;
import com.nazjara.service.CityGuide;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * Chat memory for {@code TravelAssistant} and the builder-made {@link CityGuide}. The starter
 * provides the Anthropic {@link ChatModel} and builds the two {@code @AiService} interfaces itself.
 */
@Configuration
public class AiServicesConfig {

    private static final int MEMORY_WINDOW = 20;
    private static final int GUEST_MAX_OUTPUT_TOKENS = 200;

    private final Map<Audience, String> systemMessages = loadSystemMessages();

    /**
     * One in-heap message window per conversation id, used by {@code TravelAssistant}
     * ({@code chatMemoryProvider = "conversationMemory"}).
     *
     * @return the provider the AI Service proxy asks for each {@code @MemoryId}
     */
    @Bean
    public ChatMemoryProvider conversationMemory() {
        return conversationId -> MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW);
    }

    /**
     * Builds the city guide with two hooks that {@code @AiService} can't set.
     *
     * <ul>
     *   <li>{@code systemMessageProviderWithContext}: the proxy calls it when the method has no
     *       {@code @SystemMessage}, passing the {@code InvocationContext}. It picks
     *       {@code prompts/guide-kids.txt} or {@code guide-adults.txt} from the {@code audience}
     *       {@link InvocationParameters} value, so the same method gets a different system message
     *       per request. The result is rendered as a template, like an annotation's text.</li>
     *   <li>{@code chatRequestTransformer}: receives each finished {@link ChatRequest} and the memory
     *       id, and returns the request actually sent. It runs before every model call of the
     *       invocation, tool-loop rounds included. Here it caps answers for {@code guest*} users.</li>
     * </ul>
     * Spring AI does both per call on the {@code ChatClient} ({@code .system(..)}, options), or with an
     * advisor that rewrites the {@code ChatClientRequest}.
     *
     * @param chatModel the starter's Anthropic chat model
     * @return the city guide proxy
     */
    @Bean
    public CityGuide cityGuide(ChatModel chatModel) {
        return AiServices.builder(CityGuide.class)
                .chatModel(chatModel)
                .chatMemoryProvider(userId -> MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW))
                .systemMessageProviderWithContext(context ->
                        systemMessages.get(context.invocationParameters().getOrDefault("audience", Audience.ADULTS)))
                .chatRequestTransformer((request, userId) ->
                        userId.toString().startsWith("guest") ? capOutput(request) : request)
                .build();
    }

    private static ChatRequest capOutput(ChatRequest request) {
        var cap = ChatRequestParameters.builder().maxOutputTokens(GUEST_MAX_OUTPUT_TOKENS).build();
        return request.toBuilder().parameters(request.parameters().overrideWith(cap)).build();
    }

    private static Map<Audience, String> loadSystemMessages() {
        var messages = new EnumMap<Audience, String>(Audience.class);
        for (var audience : Audience.values()) {
            var path = "prompts/guide-" + audience.name().toLowerCase() + ".txt";
            try {
                messages.put(audience, new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + path, e);
            }
        }
        return messages;
    }
}
