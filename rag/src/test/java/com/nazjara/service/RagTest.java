package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.model.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class RagTest {

    private static final String QUESTION = "What is a good truck to pull a Sportsman 232 boat?";

    @Autowired
    ManualRagService manualRagService;

    @Autowired
    RagAssistant ragAssistant;

    @Test
    void manualRagRecommendsColorado() {
        var answer = manualRagService.getAnswer(new Question(QUESTION));

        assertThat(answer.answer()).contains("Colorado");
    }

    @Test
    void augmentedRagRecommendsColorado() {
        var answer = ragAssistant.ask(QUESTION);

        assertThat(answer).contains("Colorado");
    }
}
