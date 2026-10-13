package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private McpClient mcpClient;

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

    private Assistant assistantOver(ChatModel model) {
        return AiServices.builder(Assistant.class)
                .chatModel(model)
                .toolProvider(McpToolProvider.builder().mcpClients(mcpClient).build())
                .build();
    }
}
