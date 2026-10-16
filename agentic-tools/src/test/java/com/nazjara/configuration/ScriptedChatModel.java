package com.nazjara.configuration;

import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Returns the scripted replies in order, repeating the last one, and records every request.
 */
final class ScriptedChatModel implements ChatModel {

    final List<ChatRequest> requests = new ArrayList<>();
    private final List<AiMessage> replies;

    ScriptedChatModel(AiMessage... replies) {
        this.replies = List.of(replies);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        var reply = replies.get(Math.min(requests.size(), replies.size()) - 1);
        return ChatResponse.builder().aiMessage(reply).build();
    }

    List<String> toolNames(int requestIndex) {
        var specifications = requests.get(requestIndex).toolSpecifications();
        return specifications == null ? List.of() : specifications.stream().map(ToolSpecification::name).toList();
    }
}
