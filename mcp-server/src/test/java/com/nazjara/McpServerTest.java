package com.nazjara;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nazjara.client.WeatherClient;
import com.nazjara.model.WeatherResponse;
import com.nazjara.tool.WeatherTools;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The MCP server without Spring, without a subprocess and without an API key.
 *
 * <p>{@link McpServer#handle} is the whole protocol: one JSON-RPC message in, one out. The tests
 * call it directly to show what a client sees for each method, including the ones LangChain4j's
 * server does not implement. The last test puts {@link StdioMcpServerTransport} in front of it over
 * in-memory pipes, which is the same line-per-message exchange a real client has over stdin and
 * stdout.
 */
@ExtendWith(MockitoExtension.class)
class McpServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int METHOD_NOT_FOUND = -32601;
    private static final String CALL_CURRENT_WEATHER = """
            {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"currentWeather","arguments":{"city":"Lviv","country":"Ukraine"}}}""";

    @Mock
    private WeatherClient weatherClient;

    private McpServer server;

    @BeforeEach
    void setUp() {
        server = new McpServer(List.of(new WeatherTools(weatherClient)), new McpImplementation("test-server", "1.0.0"));
    }

    @Test
    void initializeAdvertisesOnlyTheToolsCapability() throws JsonProcessingException {
        var response = send("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""");

        var result = response.path("result");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("test-server");
        assertThat(result.path("capabilities").fieldNames()).toIterable().containsExactly("tools");
    }

    @Test
    void toolsListPublishesSchemaTitleAndAnnotationsFromToolAnnotation() throws JsonProcessingException {
        var response = send("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}""");

        var tools = response.path("result").path("tools");
        assertThat(tools).hasSize(1);
        var tool = tools.get(0);
        assertThat(tool.path("name").asText()).isEqualTo("currentWeather");
        assertThat(tool.path("title").asText()).isEqualTo("Current weather");
        assertThat(tool.path("inputSchema").path("properties").fieldNames()).toIterable()
                .containsExactly("city", "country");
        assertThat(tool.path("inputSchema").path("required")).extracting(JsonNode::asText)
                .containsExactly("city", "country");
        assertThat(tool.path("annotations").path("readOnlyHint").asBoolean()).isTrue();
        assertThat(tool.path("annotations").path("destructiveHint").asBoolean()).isFalse();
    }

    @Test
    void toolsCallReturnsTheToolResultAsOneJsonTextBlock() throws JsonProcessingException {
        when(weatherClient.currentWeather("Lviv", "Ukraine")).thenReturn(weather(12));

        var response = send(CALL_CURRENT_WEATHER);

        var result = response.path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content")).hasSize(1);
        assertThat(result.path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(OBJECT_MAPPER.readTree(result.path("content").get(0).path("text").asText()).path("temp").asInt())
                .isEqualTo(12);
    }

    @Test
    void toolFailureIsAnErrorResultNotAJsonRpcError() throws JsonProcessingException {
        when(weatherClient.currentWeather("Lviv", "Ukraine")).thenThrow(new IllegalStateException("API Ninjas returned 502"));

        var response = send(CALL_CURRENT_WEATHER);

        assertThat(response.has("error")).isFalse();
        assertThat(response.path("result").path("isError").asBoolean()).isTrue();
        assertThat(response.path("result").path("content").get(0).path("text").asText())
                .isEqualTo("API Ninjas returned 502");
    }

    @ParameterizedTest
    @ValueSource(strings = {"resources/list", "resources/templates/list", "resources/read", "prompts/list", "prompts/get", "ping"})
    void methodsOtherThanInitializeAndToolsAreNotFound(String method) throws JsonProcessingException {
        var response = send("""
                {"jsonrpc":"2.0","id":4,"method":"%s"}""".formatted(method));

        assertThat(response.has("result")).isFalse();
        assertThat(response.path("error").path("code").asInt()).isEqualTo(METHOD_NOT_FOUND);
        assertThat(response.path("error").path("message").asText()).isEqualTo("Method not found: " + method);
    }

    @Test
    @Timeout(10)
    void stdioTransportAnswersEachRequestLineWithOneResponseLine() throws IOException {
        when(weatherClient.currentWeather("Lviv", "Ukraine")).thenReturn(weather(12));
        var clientToServer = new PipedOutputStream();
        var serverToClient = new PipedInputStream();

        try (var _ = new StdioMcpServerTransport(new PipedInputStream(clientToServer),
                new PipedOutputStream(serverToClient), server);
             var responses = new BufferedReader(new InputStreamReader(serverToClient, UTF_8))) {
            clientToServer.write((CALL_CURRENT_WEATHER + "\n").getBytes(UTF_8));
            clientToServer.flush();
            var response = OBJECT_MAPPER.readTree(responses.readLine());

            assertThat(response.path("id").asInt()).isEqualTo(3);
            assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        }
    }

    private JsonNode send(String request) throws JsonProcessingException {
        return OBJECT_MAPPER.valueToTree(server.handle(OBJECT_MAPPER.readTree(request)));
    }

    private static WeatherResponse weather(int temp) {
        return new WeatherResponse(BigDecimal.ONE, 180, temp, 60, 1_760_000_000, 1_759_960_000, 8, 40, 11, 14);
    }
}
