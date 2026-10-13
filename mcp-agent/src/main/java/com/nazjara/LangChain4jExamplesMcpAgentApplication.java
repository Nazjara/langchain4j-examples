package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the mcp-agent module: an application that starts the {@code mcp-server} module as
 * a subprocess, offers its MCP tools to Claude through an AI Service, and exposes the rest of
 * LangChain4j's MCP client API (resources, prompts) over REST.
 */
@SpringBootApplication
public class LangChain4jExamplesMcpAgentApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesMcpAgentApplication.class, args);
    }
}
