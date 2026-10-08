package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * What the AI Service sends from memory, without Spring, a database or an API key. The store is
 * {@link InMemoryChatMemoryStore} (the same SPI the module implements with PostgreSQL) and the model
 * is a stub that records each request and replies with a numbered answer. The streaming test drives
 * the model's handler by hand to show what a client disconnect does.
 */
class MemoryWindowTest {

    private static final String SYSTEM_PROMPT = "Keep answers short.";
    private static final int MAX_MESSAGES = 4;

    interface Assistant {

        @dev.langchain4j.service.SystemMessage(SYSTEM_PROMPT)
        String chat(@MemoryId String conversationId, @dev.langchain4j.service.UserMessage String message);
    }

    private RecordingChatModel model;
    private InMemoryChatMemoryStore store;
    private Assistant assistant;

    @BeforeEach
    void setUp() {
        model = new RecordingChatModel();
        store = new InMemoryChatMemoryStore();
        assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
                        .id(id)
                        .maxMessages(MAX_MESSAGES)
                        .chatMemoryStore(store)
                        .build())
                .build();
    }

    @Test
    void secondTurnCarriesFirstTurn() {
        assistant.chat("a", "My name is Nazar.");
        assistant.chat("a", "What is my name?");

        assertThat(model.requests.getLast().messages()).containsExactly(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from("My name is Nazar."),
                AiMessage.from("answer 1"),
                UserMessage.from("What is my name?"));
    }

    @Test
    void conversationsAreIsolatedById() {
        assistant.chat("a", "My name is Nazar.");
        assistant.chat("b", "What is my name?");

        assertThat(model.requests.getLast().messages()).containsExactly(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from("What is my name?"));
    }

    @Test
    void windowEvictsOldestMessagesButKeepsSystemMessage() {
        assistant.chat("a", "one");
        assistant.chat("a", "two");
        assistant.chat("a", "three");

        assertThat(store.getMessages("a")).containsExactly(
                SystemMessage.from(SYSTEM_PROMPT),
                AiMessage.from("answer 2"),
                UserMessage.from("three"),
                AiMessage.from("answer 3"));
    }

    @Test
    void deletingFromStoreClearsCachedMemory() {
        assistant.chat("a", "My name is Nazar.");

        store.deleteMessages("a");
        assistant.chat("a", "What is my name?");

        assertThat(model.requests.getLast().messages()).containsExactly(
                SystemMessage.from(SYSTEM_PROMPT),
                UserMessage.from("What is my name?"));
    }

    @Test
    void cancellingFluxDoesNotStopModelAndReplyIsStillStored() {
        var streamingModel = new ManualStreamingChatModel();
        var streamingAssistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(id -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES))
                .build();
        var received = new ArrayList<String>();

        var subscription = streamingAssistant.chat("a", "Tell me a story.").subscribe(received::add);
        streamingModel.handler.onPartialResponse(new PartialResponse("Once"), streamingModel.context);
        subscription.dispose();
        streamingModel.handler.onPartialResponse(new PartialResponse(" upon"), streamingModel.context);
        streamingModel.handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("Once upon")).build());

        assertThat(received).containsExactly("Once");
        assertThat(streamingModel.context.streamingHandle().isCancelled()).isFalse();
        assertThat(streamingAssistant.getChatMemory("a").messages().getLast()).isEqualTo(AiMessage.from("Once upon"));
    }

    interface StreamingAssistant extends ChatMemoryAccess {

        Flux<String> chat(@MemoryId String conversationId, @dev.langchain4j.service.UserMessage String message);
    }

    static class ManualStreamingChatModel implements StreamingChatModel {

        final PartialResponseContext context = new PartialResponseContext(new FlagStreamingHandle());
        StreamingChatResponseHandler handler;

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            this.handler = handler;
        }
    }

    static class FlagStreamingHandle implements StreamingHandle {

        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    static class RecordingChatModel implements ChatModel {

        final List<ChatRequest> requests = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("answer " + requests.size()))
                    .build();
        }
    }
}
