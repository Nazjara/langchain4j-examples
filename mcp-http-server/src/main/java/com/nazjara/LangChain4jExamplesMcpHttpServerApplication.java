package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the mcp-http-server module: a stateless MCP server on
 * {@code http://<host>:8090/mcp} that publishes Open-Meteo weather tools, resources and a prompt.
 * Unlike the stdio {@code mcp-server}, it runs on its own and serves any number of clients on any
 * machine.
 */
@SpringBootApplication
public class LangChain4jExamplesMcpHttpServerApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesMcpHttpServerApplication.class, args);
    }
}
