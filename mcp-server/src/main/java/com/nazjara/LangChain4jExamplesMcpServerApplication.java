package com.nazjara;

import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the mcp-server module: an MCP server that publishes the {@code currentWeather}
 * tool over stdio. It is not run on its own; an MCP client (the {@code mcp-agent} module, Claude
 * Code, Claude Desktop) starts it as a subprocess and talks to it through its stdin and stdout.
 */
@SpringBootApplication
public class LangChain4jExamplesMcpServerApplication {

    /**
     * Starts the application and blocks until the client closes stdin. The transport's reader is a
     * daemon thread and there is no web server, so without the wait the JVM would exit right after
     * startup.
     *
     * @param args command-line arguments
     * @throws InterruptedException if the wait is interrupted
     */
    public static void main(String[] args) throws InterruptedException {
        try (var context = SpringApplication.run(LangChain4jExamplesMcpServerApplication.class, args)) {
            context.getBean(StdioMcpServerTransport.class).awaitClose();
        }
    }
}
