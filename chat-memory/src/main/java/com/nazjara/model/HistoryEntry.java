package com.nazjara.model;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

public record HistoryEntry(String type, String text) {

    public static HistoryEntry from(ChatMessage message) {
        var text = switch (message) {
            case SystemMessage system -> system.text();
            case UserMessage user -> user.singleText();
            case AiMessage ai -> ai.text();
            case ToolExecutionResultMessage toolResult -> toolResult.text();
            default -> message.toString();
        };
        return new HistoryEntry(message.type().name(), text);
    }
}
