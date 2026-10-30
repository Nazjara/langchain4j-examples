package com.nazjara;

import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Key-free stand-in for Claude: answers every request with "OK" and records it, so a test can
 * assert exactly what the AI Service proxy would have sent, or that it sent nothing.
 */
final class RecordingChatModel implements ChatModel {

    final List<ChatRequest> requests = new ArrayList<>();

    @Override
    public ChatResponse doChat(ChatRequest request) {
        requests.add(request);
        return ChatResponse.builder().aiMessage(AiMessage.from("OK")).build();
    }

    String lastUserText() {
        var messages = requests.getLast().messages();
        return ((UserMessage) messages.getLast()).singleText();
    }

    List<String> userTexts(int requestIndex) {
        return requests.get(requestIndex).messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).singleText())
                .toList();
    }
}
