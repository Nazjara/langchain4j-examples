package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the prompt-engineering module: input guardrails, structured prompts and
 * per-call system messages and requests on LangChain4j AI Services. The prompt-design techniques
 * ported from spring-ai-examples stay as live JUnit tests.
 */
@SpringBootApplication
public class LangChain4jExamplesPromptEngineeringApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesPromptEngineeringApplication.class, args);
    }
}
