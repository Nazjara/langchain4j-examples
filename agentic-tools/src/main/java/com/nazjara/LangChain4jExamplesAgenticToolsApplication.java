package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the agentic-tools module: a travel assistant with 15 tools, used to show tool
 * search, the round-trip limit, hidden per-request context and a reprompting output guardrail.
 */
@SpringBootApplication
public class LangChain4jExamplesAgenticToolsApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesAgenticToolsApplication.class, args);
    }
}
