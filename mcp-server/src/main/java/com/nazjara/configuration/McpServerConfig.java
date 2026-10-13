package com.nazjara.configuration;

import com.nazjara.tool.WeatherTools;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import dev.langchain4j.mcp.protocol.McpImplementation;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the MCP server from {@code langchain4j-community-mcp-server}. The whole library is these
 * two classes plus a schema mapper.
 *
 * <p><b>What it serves.</b> {@link McpServer} answers three JSON-RPC methods: {@code initialize},
 * {@code tools/list} and {@code tools/call}. Everything else gets error {@code -32601} (method not
 * found), including {@code ping}, {@code resources/list}, {@code resources/read},
 * {@code prompts/list} and {@code prompts/get}.
 *
 * <p><b>What is missing</b> (LangChain4j 1.20, unchanged in 1.22):
 * <ul>
 *   <li>Resources and prompts: there is no API to register either.</li>
 *   <li>An HTTP transport: stdio is the only one, so a client must start this application as a
 *       subprocess. The server therefore runs only on the client's own machine, serves exactly
 *       one client and cannot be deployed as a shared service.</li>
 *   <li>Server-to-client messages: no log, progress or list-changed notifications.</li>
 *   <li>A Spring Boot starter in the 1.20 line; the server is assembled here by hand.</li>
 * </ul>
 * Spring AI's {@code spring-ai-starter-mcp-server-webmvc} covers all four: {@code @McpTool},
 * {@code @McpResource} and {@code @McpPrompt} beans are published over Streamable HTTP, SSE or
 * stdio from {@code spring.ai.mcp.server.*} properties.
 */
@Configuration
public class McpServerConfig {

    /**
     * The protocol handler: maps each incoming JSON-RPC message to a response.
     *
     * @param weatherTools bean whose {@code @Tool} methods are published
     * @param name server name reported in the {@code initialize} response
     * @param version server version reported in the {@code initialize} response
     * @return the server
     */
    @Bean
    McpServer mcpServer(WeatherTools weatherTools,
                        @Value("${spring.application.name}") String name,
                        @Value("${ai.mcp.server.version}") String version) {
        return new McpServer(List.of(weatherTools), new McpImplementation(name, version));
    }

    /**
     * The stdio transport: a daemon thread reads one JSON-RPC message per line from
     * {@code System.in}, hands it to the {@link McpServer} on a worker thread and writes the
     * response as one line to {@code System.out}. Reading starts as soon as this bean is created.
     *
     * <p>Because {@code System.out} is the protocol channel, nothing else may write to it: the
     * banner is off and {@code logback-spring.xml} sends all logging to {@code System.err}.
     *
     * @param mcpServer the protocol handler
     * @return the running transport, closed with the application context
     */
    @Bean
    StdioMcpServerTransport stdioMcpServerTransport(McpServer mcpServer) {
        return new StdioMcpServerTransport(mcpServer);
    }
}
