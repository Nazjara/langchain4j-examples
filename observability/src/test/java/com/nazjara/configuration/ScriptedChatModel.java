package com.nazjara.configuration;

import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Returns the scripted replies in order with fixed token usage, and fails when it has none.
 * {@code ChatModel.chat} calls the listeners around {@link #doChat}, as a real model does.
 */
final class ScriptedChatModel implements ChatModel {

    static final int INPUT_TOKENS = 100;
    static final int OUTPUT_TOKENS = 20;

    final List<ChatRequest> requests = new ArrayList<>();
    private final List<ChatModelListener> listeners;
    private final List<AiMessage> replies;

    ScriptedChatModel(ChatModelListener listener, AiMessage... replies) {
        this.listeners = List.of(listener);
        this.replies = List.of(replies);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        if (replies.isEmpty()) {
            throw new IllegalStateException("model unavailable");
        }
        return ChatResponse.builder()
                .aiMessage(replies.get(Math.min(requests.size(), replies.size()) - 1))
                .tokenUsage(new TokenUsage(INPUT_TOKENS, OUTPUT_TOKENS))
                .modelName("scripted-model")
                .build();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }
}
