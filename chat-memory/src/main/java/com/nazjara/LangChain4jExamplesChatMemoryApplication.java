package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Per-conversation chat memory persisted in PostgreSQL, with a blocking and a streaming (SSE)
 * endpoint. Boot's Docker Compose support starts PostgreSQL from {@code compose.yaml}.
 */
@SpringBootApplication
public class LangChain4jExamplesChatMemoryApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesChatMemoryApplication.class, args);
    }
}
