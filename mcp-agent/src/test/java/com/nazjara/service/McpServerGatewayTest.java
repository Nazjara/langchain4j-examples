package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nazjara.model.PromptInfo;
import com.nazjara.model.ResourceInfo;
import com.nazjara.model.ToolInfo;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.mcp.client.McpBlobResourceContents;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpPromptArgument;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpRole;
import dev.langchain4j.mcp.client.McpTextContent;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The gateway's calls against stubbed {@link McpClient}s: tools from every server with the server
 * key, resources and prompts from the content server. The stubs return what such a server sends;
 * {@code mcp-http-server}'s {@code LangChain4jClientOverHttpTest} covers the real exchange.
 */
@ExtendWith(MockitoExtension.class)
class McpServerGatewayTest {

    @Mock
    private McpClient mcpClient;

    @Mock
    private McpClient stdioMcpClient;

    private McpServerGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new McpServerGateway(List.of(stdioMcpClient, mcpClient), mcpClient);
    }

    @Test
    void toolsListsEveryServerWithItsKey() {
        when(stdioMcpClient.key()).thenReturn("api-ninjas");
        when(stdioMcpClient.listTools()).thenReturn(List.of(ToolSpecification.builder().name("currentWeather").description("Now").build()));
        when(mcpClient.key()).thenReturn("open-meteo");
        when(mcpClient.listTools()).thenReturn(List.of(ToolSpecification.builder().name("forecast").description("Days").build()));

        var tools = gateway.tools();

        assertThat(tools).containsExactly(
                new ToolInfo("api-ninjas", "currentWeather", "Now", Map.of()),
                new ToolInfo("open-meteo", "forecast", "Days", Map.of()));
    }

    @Test
    void resourcesReturnsFixedResourcesAndUriTemplates() {
        when(mcpClient.listResources())
                .thenReturn(List.of(new McpResource("movies://titles", "movie-titles", "All titles", "application/json")));
        when(mcpClient.listResourceTemplates())
                .thenReturn(List.of(new McpResourceTemplate("movies://{title}", "movie", "One movie", "application/json")));

        var catalog = gateway.resources();

        assertThat(catalog.resources())
                .containsExactly(new ResourceInfo("movies://titles", "movie-titles", "All titles", "application/json"));
        assertThat(catalog.templates())
                .containsExactly(new ResourceInfo("movies://{title}", "movie", "One movie", "application/json"));
    }

    @Test
    void readResourceJoinsTextPartsAndSkipsBinaryParts() {
        when(mcpClient.readResource("movies://Avatar")).thenReturn(new McpReadResourceResult(List.of(
                new McpTextResourceContents("movies://Avatar", "{\"title\":\"Avatar\"}", "application/json"),
                McpBlobResourceContents.create("movies://Avatar/poster", new byte[] {1, 2, 3}),
                new McpTextResourceContents("movies://Avatar", "{\"director\":\"James Cameron\"}", "application/json"))));

        var text = gateway.readResource("movies://Avatar");

        assertThat(text).isEqualTo("{\"title\":\"Avatar\"}\n{\"director\":\"James Cameron\"}");
    }

    @Test
    void promptsReturnsArgumentNames() {
        when(mcpClient.listPrompts()).thenReturn(List.of(new McpPrompt("weather-report", "Friendly weather report",
                List.of(new McpPromptArgument("city", "City name", true), new McpPromptArgument("country", "Country name", true)))));

        var prompts = gateway.prompts();

        assertThat(prompts)
                .containsExactly(new PromptInfo("weather-report", "Friendly weather report", List.of("city", "country")));
    }

    @Test
    void renderPromptConvertsRolesToChatMessageTypes() {
        var arguments = Map.<String, Object>of("city", "Lviv");
        when(mcpClient.getPrompt("weather-report", arguments)).thenReturn(new McpGetPromptResult("Weather report", List.of(
                new McpPromptMessage(McpRole.USER, new McpTextContent("Weather report for Lviv, please.")),
                new McpPromptMessage(McpRole.ASSISTANT, new McpTextContent("Sure, one moment.")))));

        var messages = gateway.renderPrompt("weather-report", arguments);

        assertThat(messages).containsExactly(
                UserMessage.from("Weather report for Lviv, please."),
                AiMessage.from("Sure, one moment."));
    }
}
