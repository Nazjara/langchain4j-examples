package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nazjara.model.Role;
import com.nazjara.service.TravelAssistant;
import com.nazjara.tool.RoleToolProvider;
import com.nazjara.tool.TravelTools;
import java.util.Map;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

/**
 * What each role is offered and what happens when the model names a tool it was not offered,
 * against a scripted stub model and without an API key.
 */
class RoleToolProviderTest {

    private static final String QUESTION = "Book the morning flight from Kyiv to Lisbon on 2026-10-20";

    private final AiServicesConfig config = new AiServicesConfig(
            new AgenticToolsProperties(10, new AgenticToolsProperties.Plan(100, 3)));

    private final RoleToolProvider provider = new RoleToolProvider(new TravelTools());

    @Test
    void guestIsOfferedEveryToolExceptTheBookingTools() {
        var model = new ScriptedChatModel(AiMessage.from("stub answer"));

        config.restrictedAssistant(model, provider).ask(QUESTION, parameters(Role.GUEST));

        System.out.println("guest -> " + model.toolNames(0));
        assertThat(model.toolNames(0)).hasSize(13).contains("searchFlights").doesNotContain("bookFlight", "bookHotel");
    }

    @Test
    void memberIsOfferedAllFifteenTools() {
        var model = new ScriptedChatModel(AiMessage.from("stub answer"));

        config.restrictedAssistant(model, provider).ask(QUESTION, parameters(Role.MEMBER));

        assertThat(model.toolNames(0)).hasSize(15).contains("bookFlight", "bookHotel");
    }

    @Test
    void missingRoleMeansGuest() {
        var model = new ScriptedChatModel(AiMessage.from("stub answer"));

        config.restrictedAssistant(model, provider).ask(QUESTION, new InvocationParameters());

        assertThat(model.toolNames(0)).hasSize(13).doesNotContain("bookFlight");
    }

    @Test
    void guestCallToAWithheldToolIsAnsweredAsAToolResultAndNothingIsBooked() {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "bookFlight", "{\"flightNumber\":\"SA123\"}")),
                AiMessage.from("I can't book as a guest."));

        var result = config.restrictedAssistant(model, provider).ask(QUESTION, parameters(Role.GUEST));

        System.out.println("hallucinated call -> " + model.requests.get(1).messages().getLast());
        assertThat(result.content()).isEqualTo("I can't book as a guest.");
        assertThat(model.requests.get(1).messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                        message -> assertThat(message.text()).contains("bookFlight", "not available"));
        assertThat(result.toolExecutions()).noneMatch(execution -> execution.result().contains("Booked"));
    }

    @Test
    void defaultStrategyFailsTheWholeRequest() {
        var model = new ScriptedChatModel(AiMessage.from(toolCall("call-1", "bookFlight", "{\"flightNumber\":\"SA123\"}")));
        var assistant = AiServices.builder(TravelAssistant.class).chatModel(model).toolProvider(provider).build();

        assertThatThrownBy(() -> assistant.ask(QUESTION, parameters(Role.GUEST)))
                .hasMessageContaining("bookFlight")
                .hasMessageContaining("hallucination");
    }

    private static InvocationParameters parameters(Role role) {
        return InvocationParameters.from(Map.of(TravelTools.USER_ID, "alice", RoleToolProvider.ROLE, role));
    }

    private static ToolExecutionRequest toolCall(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }
}
