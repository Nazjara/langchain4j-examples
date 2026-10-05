package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the functions module: a LangChain4j AI Service that answers weather questions
 * by calling a {@code @Tool} method.
 */
@SpringBootApplication
public class LangChain4jExamplesFunctionsApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesFunctionsApplication.class, args);
    }
}
