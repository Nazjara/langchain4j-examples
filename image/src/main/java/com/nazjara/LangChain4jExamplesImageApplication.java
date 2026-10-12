package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the image module: image generation with an OpenAI {@code ImageModel} and image
 * understanding with Claude through a LangChain4j AI Service.
 */
@SpringBootApplication
public class LangChain4jExamplesImageApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesImageApplication.class, args);
    }
}
