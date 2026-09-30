package com.nazjara.configuration;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Claude {@link ChatModel} by hand instead of letting the Anthropic starter do it.
 *
 * <p>Why: an AI Service decides <em>how</em> to request structured output by asking the model
 * for its {@link ChatModel#supportedCapabilities()}. If the set contains
 * {@code RESPONSE_FORMAT_JSON_SCHEMA}, the JSON schema derived from the method's return type is
 * sent as Anthropic's native structured-output field and the API enforces it. Otherwise the
 * proxy appends "answer strictly in this JSON format: ..." to the user message and hopes the
 * model complies, which is what Spring AI's {@code .entity(...)} does. The starter's properties
 * cannot declare capabilities, so the bean is built here.
 *
 * <p>The keys live under {@code ai.anthropic.*}, not {@code langchain4j.anthropic.chat-model.*}:
 * setting the starter's {@code api-key} property would make it register a second
 * {@code ChatModel}, and the AI Service auto-wiring fails when two candidates exist. The Spring AI
 * counterpart is the auto-configured {@code AnthropicChatModel} tuned via
 * {@code spring.ai.anthropic.chat.*}.
 */
@Configuration
public class ChatModelConfig {

    /**
     * Claude chat model that advertises native JSON-schema support.
     *
     * @param apiKey       Anthropic API key
     * @param modelName    Claude model id
     * @param logRequests  whether to log the raw HTTP request body (shows where the schema goes)
     * @param logResponses whether to log the raw HTTP response body
     * @return the chat model every {@code @AiService} in this module is wired to
     */
    @Bean
    ChatModel chatModel(@Value("${ai.anthropic.api-key}") String apiKey,
                        @Value("${ai.anthropic.model-name}") String modelName,
                        @Value("${ai.anthropic.log-requests:false}") boolean logRequests,
                        @Value("${ai.anthropic.log-responses:false}") boolean logResponses) {
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
