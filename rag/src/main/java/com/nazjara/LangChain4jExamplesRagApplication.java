package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the rag module: answers questions from ingested documents, once with hand-written
 * retrieval and once with the AI Service's built-in retrieval augmentor.
 */
@SpringBootApplication
public class LangChain4jExamplesRagApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesRagApplication.class, args);
    }
}
