package com.nazjara.configuration;

import com.nazjara.tool.SkippingResourcesAsToolsPresenter;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.service.tool.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the MCP client side by hand: one transport and one client per server, and one tool
 * provider over both.
 *
 * <p>LangChain4j has no Spring Boot starter for MCP clients, so nothing here comes from
 * properties. Spring AI's {@code spring-ai-starter-mcp-client} creates one client per entry under
 * {@code spring.ai.mcp.client.*.connections} and a {@code ToolCallbackProvider} over all of them.
 *
 * <p>In MCP terms this application is the <b>host</b> and each {@link McpClient} is a component
 * inside it, one per server. The model never talks to a server: it asks the host for a tool call
 * and the host forwards it to the server that owns the tool.
 *
 * <p>The two servers show the two ways to deploy one:
 * <ul>
 *   <li>{@code mcp-server} (key {@value #API_NINJAS}): a <b>local plug-in</b>. Stdio, started by
 *       this application as a subprocess, so it runs on this machine and serves only this client.
 *       Tool {@code currentWeather}.</li>
 *   <li>{@code mcp-http-server} (key {@value #OPEN_METEO}): a <b>remote service</b>. Streamable
 *       HTTP, runs on its own, serves any number of clients from any machine. Tools
 *       {@code forecast} and {@code airQuality}, two resources and a prompt.</li>
 * </ul>
 *
 * <p><b>One shared client per server is correct here.</b> Both servers push nothing to the
 * client: the stdio one can't, and the HTTP one is stateless. A stateful server that sends logs,
 * progress, elicitation or sampling requests would need one {@code DefaultMcpClient} per user:
 * those messages arrive on the session of the call that caused them, so with a shared client the
 * agent could not tell which user they belong to. {@code spring-ai-examples}' {@code mcp-agent}
 * does that per-user wiring.
 */
@Configuration
public class McpClientConfig {

    static final String API_NINJAS = "api-ninjas";
    static final String OPEN_METEO = "open-meteo";

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
     * at DEBUG on the logger named {@code MCP}: {@code >} for sent, {@code <} for received.
     *
     * <p>{@code build()} performs the {@code initialize} handshake and nothing else. The tool list
     * is not fetched at startup; see {@link #mcpToolProvider(McpClient, McpClient, JsonMapper)}.
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
    McpClient apiNinjasMcpClient(@Value("${ai.mcp.server.jar}") String serverJarPath,
                                 @Value("${ai.mcp.log-events:false}") boolean logEvents) {
        var serverJar = locateServerJar(serverJarPath);
        var javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var transport = StdioMcpTransport.builder()
                .command(List.of(javaBinary, "-jar", serverJar.toString()))
                .logEvents(logEvents)
                .build();
        return DefaultMcpClient.builder()
                .key(API_NINJAS)
                .clientName("langchain4j-mcp-agent")
                .transport(transport)
                .build();
    }

    /**
     * MCP client connected to the {@code mcp-http-server} module over Streamable HTTP.
     *
     * <p>{@link StreamableHttpMcpTransport} sends every JSON-RPC message as an HTTP {@code POST}
     * and accepts either a plain JSON response or an SSE stream. A server that keeps sessions
     * returns an {@code Mcp-Session-Id} header, which the transport stores and sends back; this one
     * is stateless and sends none, so every request stands alone. The optional GET stream for
     * server-initiated messages ({@code subsidiaryChannel}) stays off, as by default: a stateless
     * server has nothing to send on it and answers 405. With {@code logRequests} and
     * {@code logResponses}, each body is logged at INFO on the logger named {@code MCP}.
     *
     * <p>{@code build()} connects at once. The client first tries the newer {@code server/discover}
     * handshake; the server answers "Method not found", so the client falls back to
     * {@code initialize}. If the server is not running, this bean fails and the application does
     * not start: start {@code mcp-http-server} first. A server that goes down later only removes
     * its tools: {@code McpToolProvider} logs a warning and offers the other server's tools.
     *
     * @param serverUrl the server's MCP endpoint
     * @param logEvents whether to log every JSON-RPC message sent and received
     * @return the initialized client
     */
    @Bean
    McpClient openMeteoMcpClient(@Value("${ai.mcp.http-server.url}") String serverUrl,
                                 @Value("${ai.mcp.log-events:false}") boolean logEvents) {
        var transport = StreamableHttpMcpTransport.builder()
                .url(serverUrl)
                .logRequests(logEvents)
                .logResponses(logEvents)
                .build();
        return DefaultMcpClient.builder()
                .key(OPEN_METEO)
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
     * Exposes both servers' tools to AI Services. The starter wires a {@link ToolProvider} bean
     * into every {@code @AiService} in the default {@code AUTOMATIC} wiring mode.
     *
     * <p>A {@code ToolProvider} is asked for tools on <b>every AI Service invocation</b>, before the
     * model request is built. {@link McpToolProvider} calls {@link McpClient#listTools()} on each
     * client and concatenates the results: Claude sees one flat list ({@code currentWeather},
     * {@code forecast}, {@code airQuality}) and cannot tell which server a tool lives on. Each
     * entry is paired with an {@code McpToolExecutor} bound to its own client, so a call is routed
     * to the server that published the tool. The first call per client sends {@code tools/list};
     * later calls reuse the cached list ({@code cacheToolList} defaults to {@code true}). A client
     * that fails to list is skipped with a warning ({@code failIfOneServerFails} is {@code false}).
     *
     * <p>The two servers publish different tools on purpose. If two servers published the same
     * name, Claude would get two tools with one name; {@code toolNameMapper} can rename them, e.g.
     * prefix the client key, and {@code filterToolNames} can drop one ({@code McpToolProviderTest}).
     *
     * <p>{@code resourcesAsToolsPresenter} adds {@code list_resources} and {@code get_resource}, so
     * Claude can read resources itself; see {@link SkippingResourcesAsToolsPresenter}.
     *
     * @param apiNinjasMcpClient client of the stdio server
     * @param openMeteoMcpClient client of the HTTP server
     * @param jsonMapper Boot's JSON mapper, used to write the resource list
     * @return the tool provider
     */
    @Bean
    ToolProvider mcpToolProvider(@Qualifier("apiNinjasMcpClient") McpClient apiNinjasMcpClient,
                                 @Qualifier("openMeteoMcpClient") McpClient openMeteoMcpClient,
                                 JsonMapper jsonMapper) {
        return McpToolProvider.builder()
                .mcpClients(apiNinjasMcpClient, openMeteoMcpClient)
                .resourcesAsToolsPresenter(new SkippingResourcesAsToolsPresenter(jsonMapper))
                .build();
    }
}
