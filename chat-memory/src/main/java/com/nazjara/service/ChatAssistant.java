package com.nazjara.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * Chat assistant with one memory per conversation id.
 *
 * <p>On every call the proxy reads the {@link MemoryId} argument, gets that conversation's
 * {@code ChatMemory} from the {@code ChatMemoryProvider} bean, adds the system and user messages to
 * it, sends {@code memory.messages()} (the stored window) to the model, and finally adds the model's
 * {@code AiMessage}. Each add is written through to the {@code ChatMemoryStore}.
 *
 * <p>In Spring AI the conversation id is not part of the method signature: it is passed per call as
 * an advisor parameter ({@code ChatMemory.CONVERSATION_ID}) to a {@code MessageChatMemoryAdvisor}.
 */
@AiService
public interface ChatAssistant {

    /**
     * Answers within the given conversation, blocking until the whole reply is generated.
     *
     * @param conversationId the conversation whose memory is loaded and updated
     * @param message the user's message
     * @return the model's reply
     */
    @SystemMessage(fromResource = "/prompts/chat-system.txt")
    String chat(@MemoryId String conversationId, @UserMessage String message);

    /**
     * Same as {@link #chat(String, String)}, but returns the reply as it is generated.
     *
     * <p>Returning {@link Flux} makes the proxy use the {@code StreamingChatModel} bean, which sends
     * {@code "stream": true} to Anthropic and parses its SSE response; {@code langchain4j-reactor}
     * adapts the partial-text callbacks into the {@link Flux}. The {@code AiMessage} is added to
     * memory when the model's stream completes. In 1.20.2 the adapter does not pass a cancelled
     * subscription on to the model: the Anthropic call keeps running to the end and its full reply is
     * still stored. Spring AI's equivalent is {@code chatClient.prompt()...stream().content()}.
     *
     * @param conversationId the conversation whose memory is loaded and updated
     * @param message the user's message
     * @return the reply as partial text fragments
     */
    @SystemMessage(fromResource = "/prompts/chat-system.txt")
    Flux<String> chatStream(@MemoryId String conversationId, @UserMessage String message);
}
