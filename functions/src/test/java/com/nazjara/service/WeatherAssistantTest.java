package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "API_NINJAS_API_KEY", matches = ".+")
class WeatherAssistantTest {

    @Autowired
    WeatherAssistant weatherAssistant;

    @Test
    void askWithToolCallsCallsCurrentWeather() {
        var result = weatherAssistant.askWithToolCalls("What is the weather in Lviv, Ukraine?");

        assertThat(result.content()).isNotBlank();
        assertThat(result.toolExecutions())
                .isNotEmpty()
                .allSatisfy(execution -> assertThat(execution.request().name()).isEqualTo("currentWeather"));
    }
}
