package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the observability module: a café assistant with RAG and one tool, traced end to
 * end with Micrometer Observations exported over OTLP to Grafana LGTM.
 */
@SpringBootApplication
public class LangChain4jExamplesObservabilityApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesObservabilityApplication.class, args);
    }
}
