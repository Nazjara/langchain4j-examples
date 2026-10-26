package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * How {@link McpToolProvider} connects an MCP client to an AI Service, without a server, a
 * subprocess or an API key.
 *
 * <p>The {@link McpClient} is a mock, so each test shows exactly which client calls the AI Service
 * proxy triggers and when. A mock has no cache: a real {@code DefaultMcpClient} is asked just as
 * often, but answers from its cached tool list after the first {@code tools/list}.
 */
@ExtendWith(MockitoExtension.class)
class McpToolProviderTest {

    private static final ToolSpecification CURRENT_WEATHER = ToolSpecification.builder()
            .name("currentWeather")
            .description("Get the current weather for a city")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city", "City name")
                    .addStringProperty("country", "Country name")
                    .required("city", "country")
                    .build())
            .build();

    private static final ToolExecutionRequest LVIV_REQUEST = ToolExecutionRequest.builder()
            .id("call-1")
            .name("currentWeather")
            .arguments("{\"city\":\"Lviv\",\"country\":\"Ukraine\"}")
            .build();

    interface Assistant {

        String ask(String question);
    }

    private static final ToolSpecification FORECAST = ToolSpecification.builder()
            .name("forecast")
            .description("Daily forecast for a city")
            .parameters(JsonObjectSchema.builder().addStringProperty("city", "City name").required("city").build())
            .build();

    @Mock
    private McpClient mcpClient;

    @Mock
    private McpClient otherMcpClient;

    @Test
    void buildingTheAiServiceDoesNotContactTheServer() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));

        assistantOver(model);

        verifyNoInteractions(mcpClient);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void mcpToolIsOfferedToTheModelAndExecutedThroughTheClient() {
        var model = new ScriptedChatModel(AiMessage.from(LVIV_REQUEST), AiMessage.from("It is 12°C in Lviv."));
        when(mcpClient.listTools()).thenReturn(List.of(CURRENT_WEATHER));
        when(mcpClient.executeTool(eq(LVIV_REQUEST), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("{\"temp\":12}").build());

        var answer = assistantOver(model).ask("Weather in Lviv?");

        assertThat(answer).isEqualTo("It is 12°C in Lviv.");
        assertThat(model.requests()).hasSize(2);
        assertThat(model.requests().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("currentWeather");
        assertThat(model.requests().getLast().messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                        message -> assertThat(message.text()).isEqualTo("{\"temp\":12}"));
    }

    @Test
    void toolListIsRequestedOnEveryInvocation() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));
        when(mcpClient.listTools()).thenReturn(List.of(CURRENT_WEATHER));
        var assistant = assistantOver(model);

        assistant.ask("Hi");
        assistant.ask("Hi again");

        verify(mcpClient, times(2)).listTools();
    }

    @Test
    void toolsOfTwoServersAreMergedAndEachCallGoesToItsOwnServer() {
        var forecastRequest = ToolExecutionRequest.builder().id("call-2").name("forecast").arguments("{\"city\":\"Lviv\"}").build();
        var model = new ScriptedChatModel(AiMessage.from(LVIV_REQUEST, forecastRequest), AiMessage.from("12°C now, rain tomorrow."));
        when(mcpClient.listTools()).thenReturn(List.of(CURRENT_WEATHER));
        when(otherMcpClient.listTools()).thenReturn(List.of(FORECAST));
        when(mcpClient.executeTool(eq(LVIV_REQUEST), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("{\"temp\":12}").build());
        when(otherMcpClient.executeTool(eq(forecastRequest), any()))
                .thenReturn(ToolExecutionResult.builder().resultText("{\"rain\":true}").build());

        var answer = assistantOver(model, McpToolProvider.builder().mcpClients(mcpClient, otherMcpClient).build()).ask("Lviv weather?");

        assertThat(answer).isEqualTo("12°C now, rain tomorrow.");
        assertThat(model.requests().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("currentWeather", "forecast");
        assertThat(model.requests().getLast().messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .extracting(message -> ((ToolExecutionResultMessage) message).text())
                .containsExactly("{\"temp\":12}", "{\"rain\":true}");
    }

    @Test
    void twoServersWithTheSameToolNameFailTheInvocation() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));
        when(mcpClient.listTools()).thenReturn(List.of(FORECAST));
        when(otherMcpClient.listTools()).thenReturn(List.of(FORECAST));

        var assistant = assistantOver(model, McpToolProvider.builder().mcpClients(mcpClient, otherMcpClient).build());

        assertThatThrownBy(() -> assistant.ask("Forecast?")).hasMessageContaining("forecast");
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void toolNameMapperPrefixesTheServerKeySoClashingToolsCanCoexist() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));
        when(mcpClient.key()).thenReturn("api-ninjas");
        when(mcpClient.listTools()).thenReturn(List.of(FORECAST));
        when(otherMcpClient.key()).thenReturn("open-meteo");
        when(otherMcpClient.listTools()).thenReturn(List.of(FORECAST));
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient, otherMcpClient)
                .toolNameMapper((client, tool) -> client.key().replace('-', '_') + "__" + tool.name())
                .build();

        assistantOver(model, toolProvider).ask("Forecast?");

        assertThat(model.requests().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("api_ninjas__forecast", "open_meteo__forecast");
    }

    @Test
    void filterToolNamesKeepsOnlyTheNamedTools() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));
        when(mcpClient.listTools()).thenReturn(List.of(CURRENT_WEATHER));
        when(otherMcpClient.listTools()).thenReturn(List.of(FORECAST));
        var toolProvider = McpToolProvider.builder()
                .mcpClients(mcpClient, otherMcpClient)
                .filterToolNames("forecast")
                .build();

        assistantOver(model, toolProvider).ask("Forecast?");

        assertThat(model.requests().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("forecast");
    }

    private Assistant assistantOver(ChatModel model) {
        return assistantOver(model, McpToolProvider.builder().mcpClients(mcpClient).build());
    }

    private Assistant assistantOver(ChatModel model, ToolProvider toolProvider) {
        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .toolProvider(toolProvider)
                .build();
    }
}
