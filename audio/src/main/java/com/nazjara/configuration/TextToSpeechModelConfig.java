package com.nazjara.configuration;

import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.openai.OpenAiTextToSpeechModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the OpenAI {@link TextToSpeechModel} by hand.
 *
 * <p>LangChain4j's OpenAI Spring Boot starter has properties for chat, language, embedding,
 * moderation and image models, but none for text-to-speech, so the bean is built here from
 * {@code ai.openai.*} and the module depends on plain {@code langchain4j-open-ai}. The model is
 * marked {@code @Experimental} in 1.20.2.
 *
 * <p>The request it sends to {@code POST /v1/audio/speech} holds only {@code model}, {@code input}
 * and {@code voice}. Speed, speaking instructions and the audio format cannot be set, so the reply
 * is always MP3 at the default speed. Spring AI auto-configures its {@code TextToSpeechModel} from
 * the API key and takes all of those per call through {@code OpenAiAudioSpeechOptions}.
 */
@Configuration
public class TextToSpeechModelConfig {

    /**
     * OpenAI text-to-speech model with a fixed default voice.
     *
     * @param apiKey      OpenAI API key
     * @param modelName   text-to-speech model id
     * @param voice       voice used when a request names none
     * @param logRequests whether to log the raw HTTP request body
     * @return the text-to-speech model
     */
    @Bean
    TextToSpeechModel textToSpeechModel(@Value("${ai.openai.api-key}") String apiKey,
                                        @Value("${ai.openai.model-name}") String modelName,
                                        @Value("${ai.openai.voice}") String voice,
                                        @Value("${ai.openai.log-requests:false}") boolean logRequests) {
        return OpenAiTextToSpeechModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .voice(voice)
                .logRequests(logRequests)
                .build();
    }
}
