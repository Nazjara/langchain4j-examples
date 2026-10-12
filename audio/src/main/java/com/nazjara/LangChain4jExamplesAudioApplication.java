package com.nazjara;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the audio module: text-to-speech with an OpenAI {@code TextToSpeechModel}.
 */
@SpringBootApplication
public class LangChain4jExamplesAudioApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LangChain4jExamplesAudioApplication.class, args);
    }
}
