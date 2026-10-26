package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RagTest {

    private static final String QUESTION = "What is a good truck to pull a Sportsman 232 boat?";

    @Autowired
    RagAssistant ragAssistant;

    @Autowired
    RoutingRagAssistant routingRagAssistant;

    @Test
    void augmentedRagRecommendsColorado() {
        var result = ragAssistant.ask(QUESTION);

        assertThat(result.content()).contains("Colorado");
    }

    @Test
    void routedRagRecommendsColorado() {
        var result = routingRagAssistant.ask(QUESTION);

        assertThat(result.content()).contains("Colorado");
    }
}
