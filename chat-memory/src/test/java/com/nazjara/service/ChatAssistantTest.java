package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.docker.compose.skip.in-tests=false")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ChatAssistantTest {

    @Autowired
    ChatAssistant chatAssistant;

    @Autowired
    ChatMemoryStore chatMemoryStore;

    @Test
    void blockingChatRemembersEarlierTurn() {
        var conversationId = UUID.randomUUID().toString();

        chatAssistant.chat(conversationId, "My name is Nazar.");
        var answer = chatAssistant.chat(conversationId, "What is my name?");

        assertThat(answer).contains("Nazar");
        assertThat(chatMemoryStore.getMessages(conversationId)).hasSize(5);
    }

    @Test
    void streamingChatStoresReplyOnCompletion() {
        var conversationId = UUID.randomUUID().toString();

        chatAssistant.chat(conversationId, "My name is Nazar.");
        var answer = String.join("", chatAssistant.chatStream(conversationId, "What is my name?").collectList().block());

        assertThat(answer).contains("Nazar");
        assertThat(chatMemoryStore.getMessages(conversationId).getLast())
                .isInstanceOfSatisfying(AiMessage.class, message -> assertThat(message.text()).isEqualTo(answer));
    }
}
