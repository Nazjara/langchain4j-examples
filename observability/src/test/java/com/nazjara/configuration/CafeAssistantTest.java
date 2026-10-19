package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.service.CafeAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(properties = {
        "management.tracing.export.enabled=false",
        "management.otlp.metrics.export.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class CafeAssistantTest {

    @Autowired
    CafeAssistant cafeAssistant;

    @Autowired
    RecordingObservationHandler handler;

    @BeforeEach
    void setUp() {
        handler.stopped.clear();
    }

    @Test
    void specialQuestionCallsTheToolAndIsTraced() {
        var result = cafeAssistant.ask("What's today's special?");

        assertThat(result.toolExecutions()).extracting(execution -> execution.request().name()).contains("getTodaysSpecial");
        assertThat(handler.contextualNames()).contains("retrieve", "chat claude-sonnet-5-5",
                "execute_tool getTodaysSpecial", "ai_service CafeAssistant.ask");
    }

    @Test
    void knowledgeBaseQuestionIsAnsweredFromRetrievedChunks() {
        var result = cafeAssistant.ask("Until what time is breakfast served?");

        assertThat(result.sources()).isNotEmpty();
        assertThat(result.content()).contains("12");
    }

    @TestConfiguration
    static class RecordingConfig {

        @Bean
        RecordingObservationHandler recordingObservationHandler() {
            return new RecordingObservationHandler();
        }
    }
}
