package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the basics module: plain Q&amp;A, prompt templates and structured output
 * through a LangChain4j AI Service.
 */
@SpringBootApplication
public class LangChain4jExamplesBasicsApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesBasicsApplication.class, args);
    }
}
