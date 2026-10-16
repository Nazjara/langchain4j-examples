package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.tool.TravelTools;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class TravelAssistantTest {

    @Autowired
    @Qualifier("simpleSearchAssistant")
    TravelAssistant simpleSearchAssistant;

    @Autowired
    TripPlanner tripPlanner;

    @Test
    void simpleSearchFindsAndCallsTheFlightTool() {
        var result = simpleSearchAssistant.ask("Find me a flight from Kyiv to Lisbon on 2026-10-20.",
                InvocationParameters.from(TravelTools.USER_ID, "alice"));

        assertThat(result.content()).isNotBlank();
        assertThat(result.toolExecutions()).extracting(execution -> execution.request().name())
                .contains("tool_search_tool", "searchFlights");
    }

    @Test
    void planPassesTheGuardrail() {
        var plan = tripPlanner.plan("live-plan", "3 days in Lisbon from 2026-10-20, flying from Kyiv, mid-range budget.");

        assertThat(plan.days()).hasSize(3);
        assertThat(plan.estimatedBudgetEur()).isPositive();
    }
}
