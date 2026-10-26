package com.nazjara.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nazjara.ScriptedChatModel;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.service.AiServices;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * The resources-as-tools pair in an AI Service over two stubbed servers: one that serves resources
 * and one that answers "Method not found", like the stdio {@code mcp-server}. The scripted model
 * calls the tools; each test checks what the tool returned to it.
 */
@ExtendWith(MockitoExtension.class)
class SkippingResourcesAsToolsPresenterTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final ToolExecutionRequest LIST_RESOURCES = ToolExecutionRequest.builder()
            .id("call-1").name("list_resources").arguments("{}").build();

    interface Assistant {

        String ask(String question);
    }

    @Mock
    private McpClient stdioClient;

    @Mock
    private McpClient httpClient;

    @BeforeEach
    void setUp() {
        when(stdioClient.listTools()).thenReturn(List.of());
        when(httpClient.listTools()).thenReturn(List.of());
    }

    @Test
    void listAndGetResourceToolsAreOfferedToTheModel() {
        var model = new ScriptedChatModel(AiMessage.from("Hello."));

        assistantOver(model).ask("Hi");

        assertThat(model.requests().getFirst().toolSpecifications())
                .extracting(ToolSpecification::name)
                .containsExactly("list_resources", "get_resource");
    }

    @Test
    void listResourcesSkipsAServerWithoutResources() {
        var model = new ScriptedChatModel(AiMessage.from(LIST_RESOURCES), AiMessage.from("Two resources."));
        when(stdioClient.listResources(any())).thenThrow(new McpException(-32601, "Method not found: resources/list"));
        when(stdioClient.listResourceTemplates(any())).thenThrow(new McpException(-32601, "Method not found: resources/templates/list"));
        when(httpClient.key()).thenReturn("open-meteo");
        when(httpClient.listResources(any()))
                .thenReturn(List.of(new McpResource("weather://wmo-codes", "wmo-weather-codes", "WMO codes", "application/json")));
        when(httpClient.listResourceTemplates(any()))
                .thenReturn(List.of(new McpResourceTemplate("weather://city/{name}", "city", "Places by name", "application/json")));

        assistantOver(model).ask("Which resources are there?");

        var result = lastToolResult(model);
        assertThat(JSON_MAPPER.readTree(result)).hasSize(2);
        assertThat(result)
                .contains("\"mcpServer\":\"open-meteo\"")
                .contains("\"uri\":\"weather://wmo-codes\"")
                .contains("\"uriTemplate\":\"weather://city/{name}\"");
    }

    @Test
    void listResourcesReportsAnyOtherServerErrorToTheModel() {
        var model = new ScriptedChatModel(AiMessage.from(LIST_RESOURCES), AiMessage.from("Listing failed."));
        when(stdioClient.listResources(any())).thenThrow(new McpException(-32603, "Internal error"));

        assistantOver(model).ask("Which resources are there?");

        assertThat(lastToolResult(model)).contains("Internal error");
    }

    @Test
    void getResourceReadsATemplateUriFromTheNamedServer() {
        var getResource = ToolExecutionRequest.builder()
                .id("call-1").name("get_resource").arguments("{\"mcpServer\":\"open-meteo\",\"uri\":\"weather://city/Lviv\"}").build();
        var model = new ScriptedChatModel(AiMessage.from(getResource), AiMessage.from("Lviv is in Ukraine."));
        when(stdioClient.key()).thenReturn("api-ninjas");
        when(httpClient.key()).thenReturn("open-meteo");
        when(httpClient.readResource(eq("weather://city/Lviv"), any())).thenReturn(new McpReadResourceResult(List.of(
                new McpTextResourceContents("weather://city/Lviv", "[{\"name\":\"Lviv\"}]", "application/json"))));

        assistantOver(model).ask("Where is Lviv?");

        assertThat(lastToolResult(model)).isEqualTo("[{\"name\":\"Lviv\"}]");
    }

    private Assistant assistantOver(ScriptedChatModel model) {
        var toolProvider = McpToolProvider.builder()
                .mcpClients(stdioClient, httpClient)
                .resourcesAsToolsPresenter(new SkippingResourcesAsToolsPresenter(JSON_MAPPER))
                .build();
        return AiServices.builder(Assistant.class).chatModel(model).toolProvider(toolProvider).build();
    }

    private static String lastToolResult(ScriptedChatModel model) {
        return model.requests().getLast().messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .map(message -> ((ToolExecutionResultMessage) message).text())
                .reduce((first, second) -> second)
                .orElseThrow();
    }
}
