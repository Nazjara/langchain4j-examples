package com.nazjara.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nazjara.ScriptedChatModel;
import com.nazjara.model.ResourceQuestion;
import com.nazjara.service.McpAssistant;
import com.nazjara.service.McpServerGateway;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpRole;
import dev.langchain4j.mcp.client.McpTextContent;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.AiServices;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * The resource and prompt endpoints against a stubbed {@link McpClient} and a stub model, without
 * Spring and without an API key. Shows what reaches the model when a server does serve resources
 * and prompts, and what the endpoints return when it does not.
 */
@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private McpClient mcpClient;

    private ScriptedChatModel chatModel;
    private QuestionController controller;

    @BeforeEach
    void setUp() {
        chatModel = new ScriptedChatModel(AiMessage.from("James Cameron."));
        var mcpAssistant = AiServices.builder(McpAssistant.class).chatModel(chatModel).build();
        controller = new QuestionController(mcpAssistant, new McpServerGateway(List.of(mcpClient), mcpClient), chatModel);
    }

    @Test
    void askAboutResourcePutsTheResourceTextIntoTheSystemMessage() {
        when(mcpClient.readResource("movies://Avatar")).thenReturn(new McpReadResourceResult(List.of(
                new McpTextResourceContents("movies://Avatar", "{\"director\":\"James Cameron\"}", "application/json"))));

        var answer = controller.askAboutResource(new ResourceQuestion("movies://Avatar", "Who directed it?"));

        assertThat(answer.answer()).isEqualTo("James Cameron.");
        assertThat(chatModel.requests()).singleElement().satisfies(request -> {
            assertThat(request.messages().getFirst()).isInstanceOfSatisfying(SystemMessage.class,
                    message -> assertThat(message.text()).contains("{\"director\":\"James Cameron\"}"));
            assertThat(request.messages().getLast()).isEqualTo(UserMessage.from("Who directed it?"));
        });
    }

    @Test
    void runPromptSendsTheRenderedPromptMessagesToTheModel() {
        var arguments = Map.<String, Object>of("city", "Lviv");
        when(mcpClient.getPrompt("weather-report", arguments)).thenReturn(new McpGetPromptResult("Weather report",
                List.of(new McpPromptMessage(McpRole.USER, new McpTextContent("Weather report for Lviv, please.")))));

        var answer = controller.runPrompt("weather-report", arguments);

        assertThat(answer.answer()).isEqualTo("James Cameron.");
        assertThat(chatModel.requests()).singleElement()
                .satisfies(request -> assertThat(request.messages())
                        .containsExactly(UserMessage.from("Weather report for Lviv, please.")));
    }

    @Test
    void methodNotFoundFromTheServerBecomesNotImplemented() {
        var problem = controller.handleMcpError(new McpException(-32601, "Method not found: resources/read"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_IMPLEMENTED.value());
        assertThat(problem.getDetail()).isEqualTo("Method not found: resources/read");
    }

    @Test
    void invalidParamsFromTheServerBecomesBadRequest() {
        var problem = controller.handleMcpError(new McpException(-32602, "days must be a whole number from 1 to 16, got 40"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    @Test
    void resourceNotFoundFromTheServerBecomesNotFound() {
        var problem = controller.handleMcpError(new McpException(-32002, "Resource not found"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void anyOtherServerErrorBecomesBadGateway() {
        var problem = controller.handleMcpError(new McpException(-32603, "Internal error"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
    }
}
