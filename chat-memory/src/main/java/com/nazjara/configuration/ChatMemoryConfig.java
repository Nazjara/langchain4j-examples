package com.nazjara.configuration;

import dev.langchain4j.community.store.memory.chat.sql.PostgreSQLDialect;
import dev.langchain4j.community.store.memory.chat.sql.SQLChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chat memory in two layers.
 *
 * <ul>
 *   <li>{@link ChatMemoryStore} decides <em>where</em> messages live. {@link SQLChatMemoryStore}
 *       (from {@code langchain4j-community-sql}) keeps one row per memory id in
 *       {@code chat_memory}, with the whole message list serialized as JSON and upserted on every
 *       change. The table therefore holds the current window, not an audit log.</li>
 *   <li>{@link ChatMemoryProvider} decides <em>what</em> is sent. The AI Service proxy calls it
 *       with the {@code @MemoryId} argument the first time it sees an id and caches the returned
 *       {@link MessageWindowChatMemory}. That memory holds no messages itself: each read goes to the
 *       store and is trimmed to the last {@value #MAX_MESSAGES} messages (the system message is
 *       kept), and each write upserts the trimmed list.</li>
 * </ul>
 * The starter wires the {@link ChatMemoryProvider} bean into every {@code @AiService}. Spring AI's
 * counterpart is a {@code MessageWindowChatMemory} over a {@code JdbcChatMemoryRepository}, applied
 * by a {@code MessageChatMemoryAdvisor} that reads the conversation id from an advisor parameter.
 */
@Configuration
public class ChatMemoryConfig {

    private static final String TABLE_NAME = "chat_memory";
    private static final int MAX_MESSAGES = 20;

    @Bean
    ChatMemoryStore chatMemoryStore(DataSource dataSource) {
        return SQLChatMemoryStore.builder()
                .dataSource(dataSource)
                .sqlDialect(new PostgreSQLDialect())
                .tableName(TABLE_NAME)
                .autoCreateTable(true)
                .build();
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
