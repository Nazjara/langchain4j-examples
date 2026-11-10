package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class AssistantTest {

    @Autowired
    Assistant assistant;

    @Test
    void ask() {
        assertThat(assistant.ask("Give me a dad joke")).isNotBlank();
    }

    @Test
    void capitals() {
        assertThat(assistant.capitals("Scandinavia"))
                .isNotEmpty()
                .allSatisfy(c -> assertThat(c.city()).isNotBlank());
    }
}
