package com.nazjara;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Stub model: returns the scripted replies in order, repeating the last one, and records every
 * request.
 */
public final class ScriptedChatModel implements ChatModel {

    private final List<AiMessage> replies;
    private final List<ChatRequest> requests = new ArrayList<>();

    public ScriptedChatModel(AiMessage... replies) {
        this.replies = List.of(replies);
    }

    public List<ChatRequest> requests() {
        return requests;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        var reply = replies.get(Math.min(requests.size(), replies.size()) - 1);
        return ChatResponse.builder().aiMessage(reply).build();
    }
}
