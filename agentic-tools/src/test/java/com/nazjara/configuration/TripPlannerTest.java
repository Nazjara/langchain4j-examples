package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nazjara.guardrail.TripPlanGuardrail;
import com.nazjara.model.TripPlan;
import com.nazjara.tool.TravelTools;
import java.math.BigDecimal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

/**
 * The output guardrail's reprompt loop against a scripted stub model, without an API key.
 */
class TripPlannerTest {

    private static final String REQUEST = "3 days in Lisbon from 2026-10-20, flying from Kyiv";
    private static final String VALID_PLAN = """
            {"destination": "Lisbon", "startDate": "2026-10-20",
             "days": [{"day": 1, "weather": "sunny, 24°C", "activities": ["Alfama walk"]},
                      {"day": 2, "weather": "cloudy, 21°C", "activities": ["Belém"]},
                      {"day": 3, "weather": "light rain, 19°C", "activities": ["Gulbenkian Museum"]}],
             "packingList": ["umbrella"], "estimatedBudgetEur": 980}
            """;
    private static final String MISNUMBERED_PLAN = VALID_PLAN.replace("\"day\": 3", "\"day\": 4");

    private final AiServicesConfig config = new AiServicesConfig(
            new AgenticToolsProperties(10, new AgenticToolsProperties.Plan(100, 3)));

    interface PlannerWithoutMemory {

        TripPlan plan(String request);
    }

    @Test
    void invalidPlanIsRepromptedWithTheWholeConversation() {
        var model = new ScriptedChatModel(AiMessage.from(MISNUMBERED_PLAN), AiMessage.from(VALID_PLAN));
        var planner = config.tripPlanner(model, new TravelTools());

        var plan = planner.plan("plan-1", REQUEST);

        var retry = model.requests.get(1).messages();
        System.out.println("reprompt request -> " + retry);
        assertThat(plan.days()).extracting(TripPlan.DayPlan::day).containsExactly(1, 2, 3);
        assertThat(plan.estimatedBudgetEur()).isEqualByComparingTo(new BigDecimal("980"));
        assertThat(model.requests).hasSize(2);
        assertThat(retry.getFirst()).isInstanceOf(SystemMessage.class);
        assertThat(retry.get(1)).isInstanceOfSatisfying(UserMessage.class,
                message -> assertThat(message.singleText()).startsWith(REQUEST));
        assertThat(retry.get(retry.size() - 2)).isInstanceOfSatisfying(AiMessage.class,
                message -> assertThat(message.text()).isEqualTo(MISNUMBERED_PLAN));
        assertThat(retry.getLast()).isInstanceOfSatisfying(UserMessage.class,
                message -> assertThat(message.singleText()).contains("days must be numbered 1..3 in order"));
        assertThat(planner.evictChatMemory("plan-1")).isTrue();
    }

    @Test
    void planStillInvalidAfterLastRepromptFails() {
        var model = new ScriptedChatModel(AiMessage.from("{\"destination\": \"Lisbon\"}"));
        var planner = config.tripPlanner(model, new TravelTools());

        assertThatThrownBy(() -> planner.plan("plan-2", REQUEST))
                .isInstanceOf(OutputGuardrailException.class)
                .hasMessageContaining("maximum number of retries")
                .hasMessageContaining("startDate must be an ISO-8601 date");
        assertThat(model.requests).hasSize(3);
    }

    @Test
    void brokenJsonIsRepromptedByTheJsonExtractor() {
        var model = new ScriptedChatModel(AiMessage.from("Here is your plan: {\"destination\": "), AiMessage.from(VALID_PLAN));
        var planner = config.tripPlanner(model, new TravelTools());

        var plan = planner.plan("plan-3", REQUEST);

        assertThat(plan.destination()).isEqualTo("Lisbon");
        assertThat(model.requests.get(1).messages().getLast()).isInstanceOfSatisfying(UserMessage.class,
                message -> assertThat(message.singleText()).contains("valid JSON"));
    }

    @Test
    void repromptWithoutChatMemoryCarriesOnlyTheRepromptText() {
        var model = new ScriptedChatModel(AiMessage.from(MISNUMBERED_PLAN), AiMessage.from(VALID_PLAN));
        var planner = AiServices.builder(PlannerWithoutMemory.class)
                .chatModel(model)
                .outputGuardrails(new TripPlanGuardrail())
                .build();

        planner.plan(REQUEST);

        System.out.println("reprompt request without memory -> " + model.requests.get(1).messages());
        assertThat(model.requests.get(1).messages()).singleElement().isInstanceOfSatisfying(UserMessage.class,
                message -> assertThat(message.singleText()).startsWith("The trip plan is invalid"));
    }
}
