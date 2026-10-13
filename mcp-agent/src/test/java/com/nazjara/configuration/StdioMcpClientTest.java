package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * LangChain4j's MCP client against LangChain4j's MCP server over real stdio: each test starts the
 * {@code mcp-server} jar as a subprocess, the way the application does.
 *
 * <p>This is the integration the two modules can actually have, written down as assertions: tools
 * work, and every resource and prompt request is rejected with JSON-RPC error {@code -32601}. The
 * server rejects {@code ping} the same way, but the client's health check accepts any reply.
 *
 * <p>Runs only when the server jar has been built ({@code ./mvnw -pl mcp-server package}). No API
 * key is needed because no tool is executed.
 */
@EnabledIf("serverJarBuilt")
class StdioMcpClientTest {

    private static final Path SERVER_JAR = Path.of("../mcp-server/target/mcp-server-1.0.0-SNAPSHOT.jar");
    private static final int METHOD_NOT_FOUND = -32601;

    private McpClient mcpClient;

    @BeforeEach
    void setUp() {
        var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var transport = StdioMcpTransport.builder()
                .command(List.of(javaBinary, "-jar", SERVER_JAR.toString()))
                .environment(Map.of("API_NINJAS_API_KEY", "not-used"))
                .build();
        mcpClient = DefaultMcpClient.builder()
                .key("weather")
                .transport(transport)
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mcpClient.close();
    }

    @Test
    void listToolsReturnsTheServerToolWithItsTitleAndHints() {
        var tools = mcpClient.listTools();

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("currentWeather");
            assertThat(tool.parameters().required()).containsExactly("city", "country");
            assertThat(tool.metadata())
                    .containsEntry("title", "Current weather")
                    .containsEntry("readOnlyHint", true);
        });
    }

    @Test
    void resourceRequestsAreRejectedBecauseTheServerHasNoResources() {
        assertThatThrownBy(mcpClient::listResources).isInstanceOfSatisfying(McpException.class, this::isMethodNotFound);
        assertThatThrownBy(mcpClient::listResourceTemplates).isInstanceOfSatisfying(McpException.class, this::isMethodNotFound);
        assertThatThrownBy(() -> mcpClient.readResource("movies://Avatar"))
                .isInstanceOfSatisfying(McpException.class, this::isMethodNotFound);
    }

    @Test
    void promptRequestsAreRejectedBecauseTheServerHasNoPrompts() {
        assertThatThrownBy(mcpClient::listPrompts).isInstanceOfSatisfying(McpException.class, this::isMethodNotFound);
        assertThatThrownBy(() -> mcpClient.getPrompt("weather-report", Map.of("city", "Lviv")))
                .isInstanceOfSatisfying(McpException.class, this::isMethodNotFound);
    }

    @Test
    void healthCheckPassesBecauseAnyReplyToPingCountsAsAlive() {
        assertThatCode(mcpClient::checkHealth).doesNotThrowAnyException();
    }

    static boolean serverJarBuilt() {
        return Files.isRegularFile(SERVER_JAR);
    }

    private void isMethodNotFound(McpException exception) {
        assertThat(exception.errorCode()).isEqualTo(METHOD_NOT_FOUND);
    }
}
