package com.nazjara;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel.AnthropicChatModelBuilder;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for the prompt-engineering examples. Each subclass is a set of live experiments
 * against the chat model: read the prompts, run the tests and inspect the printed output.
 *
 * <p>No Spring context is started. LangChain4j's models and AI Services are plain Java objects,
 * so each test builds exactly the model it needs from {@link #modelBuilder()}. That matters here
 * because thinking display and effort can only be set on the model, not per request. The Spring
 * AI counterpart is a {@code @SpringBootTest} that injects the auto-configured
 * {@code ChatClient.Builder} and varies {@code AnthropicChatOptions} per call.
 */
abstract class BaseTestClass {

    @BeforeAll
    static void requireApiKey() {
        assumeTrue(System.getenv("ANTHROPIC_API_KEY") != null, "ANTHROPIC_API_KEY is not set");
    }

    /**
     * Claude defaults shared by every test. {@code maxTokens} is raised from LangChain4j's 1024
     * default, which adaptive thinking alone can exhaust. Request logging shows the exact JSON
     * each option produces.
     *
     * @return a builder that callers extend with test-specific options before {@code build()}
     */
    static AnthropicChatModelBuilder modelBuilder() {
        return AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName("claude-sonnet-5-5")
                .maxTokens(16000)
                .logRequests(true)
                .logResponses(true);
    }
}
