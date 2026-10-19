package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = {
        "langchain4j.anthropic.chat-model.api-key=not-used",
        "management.tracing.export.enabled=false",
        "management.otlp.metrics.export.enabled=false"
})
class ObservabilityWiringTest {

    @Autowired
    ChatModel chatModel;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    RecordingObservationHandler handler;

    @Test
    void starterInjectsTheObservationListenerIntoTheChatModel() {
        assertThat(chatModel.listeners()).hasExactlyElementsOfTypes(ObservationChatModelListener.class);
    }

    @Test
    void onlyRetrieverIsOursAndReportsToTheListener() {
        var retrievers = applicationContext.getBeansOfType(ContentRetriever.class).values();

        var contents = retrievers.iterator().next().retrieve(Query.from("When is breakfast served?"));

        assertThat(retrievers).hasSize(1);
        assertThat(contents).hasSize(3);
        assertThat(RecordingObservationHandler.high(handler.named("retrieve"), "langchain4j.retrieval.results"))
                .startsWith("cafe.md#");
    }

    @TestConfiguration
    static class RecordingConfig {

        @Bean
        RecordingObservationHandler recordingObservationHandler() {
            return new RecordingObservationHandler();
        }
    }
}
