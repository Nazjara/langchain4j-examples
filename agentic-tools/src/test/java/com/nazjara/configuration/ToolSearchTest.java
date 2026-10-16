package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.tool.TravelTools;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;

/**
 * What each travel assistant actually sends, against a scripted stub model and without an API key.
 * The assistants are the beans from {@link AiServicesConfig}, built with the stub instead of Claude.
 */
class ToolSearchTest {

    private static final String QUESTION = "Book the morning flight from Kyiv to Lisbon on 2026-10-20";

    private final AiServicesConfig config = new AiServicesConfig(
            new AgenticToolsProperties(10, new AgenticToolsProperties.Plan(100, 3)));

    @Test
    void allToolsAssistantSendsEveryToolSpecification() {
        var model = new ScriptedChatModel(AiMessage.from("stub answer"));

        config.allToolsAssistant(model, new TravelTools()).ask(QUESTION, InvocationParameters.from(TravelTools.USER_ID, "alice"));

        System.out.println("all tools -> " + model.toolNames(0));
        assertThat(model.toolNames(0)).hasSize(15).contains("searchFlights", "convertCurrency", "bookFlight");
    }

    @Test
    void simpleSearchRevealsFoundToolsOnTheNextRoundTrip() {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "tool_search_tool", "{\"terms\":[\"flight\"]}")),
                AiMessage.from(toolCall("call-2", "bookFlight", "{\"flightNumber\":\"SA123\"}")),
                AiMessage.from("Booked."));

        var result = config.simpleSearchAssistant(model, new TravelTools())
                .ask(QUESTION, InvocationParameters.from(TravelTools.USER_ID, "alice"));

        System.out.println("simple search, request 1 -> " + model.toolNames(0));
        System.out.println("simple search, request 2 -> " + model.toolNames(1));
        assertThat(model.toolNames(0)).containsExactlyInAnyOrder("getLocalTime", "tool_search_tool");
        assertThat(model.toolNames(1)).contains("tool_search_tool", "searchFlights", "bookFlight").hasSizeLessThan(15);
        assertThat(model.toolNames(2)).isEqualTo(model.toolNames(1));
        assertThat(model.requests.get(1).messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                        message -> assertThat(message.text()).contains("searchFlights", "bookFlight"));
        assertThat(result.intermediateResponses().size() + 1).isEqualTo(model.requests.size()).isEqualTo(3);
    }

    @Test
    void vectorSearchFindsToolsByMeaning() {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "tool_search_tool", "{\"query\":\"look up plane tickets between two cities\"}")),
                AiMessage.from("stub answer"));

        config.vectorSearchAssistant(model, new TravelTools()).ask(QUESTION, new InvocationParameters());

        var searchResult = (ToolExecutionResultMessage) model.requests.get(1).messages().getLast();
        System.out.println("vector search result -> " + searchResult.text());
        assertThat(model.toolNames(0)).containsExactlyInAnyOrder("getLocalTime", "tool_search_tool");
        assertThat(model.toolNames(1)).contains("searchFlights").hasSize(2 + 5);
    }

    @Test
    void userIdReachesTheToolButNotTheModel() {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "bookFlight", "{\"flightNumber\":\"SA123\"}")),
                AiMessage.from("Booked."));

        var result = config.allToolsAssistant(model, new TravelTools())
                .ask(QUESTION, InvocationParameters.from(TravelTools.USER_ID, "alice"));

        var bookFlight = model.requests.getFirst().toolSpecifications().stream()
                .filter(specification -> specification.name().equals("bookFlight"))
                .findFirst()
                .orElseThrow();
        System.out.println("bookFlight schema -> " + bookFlight.parameters());
        assertThat(bookFlight.parameters().properties()).containsOnlyKeys("flightNumber");
        assertThat(result.toolExecutions()).singleElement()
                .satisfies(execution -> assertThat(execution.result()).contains("SA123", "alice"));
        assertThat(model.requests.getFirst().messages().toString()).doesNotContain("alice");
    }

    @Test
    void bookingWithoutUserIsReportedToTheModel() {
        var model = new ScriptedChatModel(
                AiMessage.from(toolCall("call-1", "bookFlight", "{\"flightNumber\":\"SA123\"}")),
                AiMessage.from("I can't book here."));

        var result = config.allToolsAssistant(model, new TravelTools()).ask(QUESTION, new InvocationParameters());

        assertThat(result.toolExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.hasFailed()).isTrue();
            assertThat(execution.result()).contains("signed-in user");
        });
    }

    private static ToolExecutionRequest toolCall(String id, String name, String arguments) {
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }
}
