package com.nazjara.configuration;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the MCP client side by hand: transport → client → tool provider.
 *
 * <p>LangChain4j has no Spring Boot starter for MCP clients, so nothing here comes from
 * properties. Spring AI's {@code spring-ai-starter-mcp-client} creates one client per entry under
 * {@code spring.ai.mcp.client.*.connections} and a {@code ToolCallbackProvider} over all of them.
 *
 * <p>In MCP terms this application is the <b>host</b> and the {@link McpClient} is a component
 * inside it, one per server. The model never talks to the server: it asks the host for a tool call
 * and the host forwards it.
 */
@Configuration
public class McpClientConfig {

    /**
     * MCP client connected to the {@code mcp-server} module over stdio.
     *
     * <p>The jar path is tried against the working directory and then its parent, so the default
     * works whether the application is started from the repository root (IntelliJ's default) or
     * from the {@code mcp-agent} directory ({@code ../mvnw spring-boot:run}).
     *
     * <p>{@link StdioMcpTransport} starts the server as a subprocess (inheriting this process's
     * environment, so {@code API_NINJAS_API_KEY} reaches it), writes JSON-RPC requests to its stdin
     * and reads responses from its stdout. The server's stderr is forwarded to this application's
     * log at DEBUG, prefixed with {@code [STDERR]}. With {@code logEvents}, every message is logged
     * at DEBUG on the logger named {@code MCP}: {@code >} for sent, {@code <} for received. Stdio is
     * the only choice here because LangChain4j's server has no HTTP transport; the client itself
     * also offers {@code StreamableHttpMcpTransport} and {@code WebSocketMcpTransport}.
     *
     * <p>{@code build()} performs the {@code initialize} handshake and nothing else. The tool list
     * is not fetched at startup; see {@link #mcpToolProvider(McpClient)}.
     *
     * <p>{@code autoHealthCheck} is on by default: the client sends {@code ping} every 30 seconds
     * and restarts the transport when no reply arrives in time. LangChain4j's own server does not
     * implement {@code ping} and answers "Method not found", but the check only waits for a reply
     * and does not look inside it, so the error still counts as alive.
     *
     * @param serverJarPath path to the executable jar of the {@code mcp-server} module, absolute or
     *                      relative to the repository root
     * @param logEvents whether to log every JSON-RPC message sent and received
     * @return the initialized client; closing it stops the subprocess
     */
    @Bean
    McpClient mcpClient(@Value("${ai.mcp.server.jar}") String serverJarPath,
                        @Value("${ai.mcp.log-events:false}") boolean logEvents) {
        var serverJar = locateServerJar(serverJarPath);
        var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var transport = StdioMcpTransport.builder()
                .command(List.of(javaBinary, "-jar", serverJar.toString()))
                .logEvents(logEvents)
                .build();
        return DefaultMcpClient.builder()
                .key("weather")
                .clientName("langchain4j-mcp-agent")
                .transport(transport)
                .build();
    }

    private static Path locateServerJar(String serverJarPath) {
        var workingDirectory = Path.of("").toAbsolutePath();
        return Stream.of(workingDirectory, workingDirectory.getParent())
                .filter(Objects::nonNull)
                .map(directory -> directory.resolve(serverJarPath))
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "MCP server jar %s not found from %s or its parent. Build it with `./mvnw -pl mcp-server package -DskipTests` or set ai.mcp.server.jar to an absolute path."
                                .formatted(serverJarPath, workingDirectory)));
    }

    /**
     * Exposes the server's tools to AI Services. The starter wires a {@link ToolProvider} bean into
     * every {@code @AiService} in the default {@code AUTOMATIC} wiring mode.
     *
     * <p>A {@code ToolProvider} is asked for tools on <b>every AI Service invocation</b>, before the
     * model request is built. {@link McpToolProvider} answers by calling
     * {@link McpClient#listTools()}: the first call sends {@code tools/list}, later calls reuse the
     * cached list ({@code cacheToolList} defaults to {@code true}) until the server announces a
     * change. Each entry is paired with an {@code McpToolExecutor} that sends {@code tools/call}.
     * From there the tool loop is the one a local {@code @Tool} method goes through.
     *
     * <p>The builder can also filter tools ({@code filterToolNames}, {@code filter}), rename them
     * when two servers clash ({@code toolNameMapper}), and present resources as tools the model can
     * list and read ({@code resourcesAsToolsPresenter}).
     *
     * @param mcpClient client whose tools are offered to the model
     * @return the tool provider
     */
    @Bean
    ToolProvider mcpToolProvider(McpClient mcpClient) {
        return McpToolProvider.builder()
                .mcpClients(mcpClient)
                .build();
    }
}
