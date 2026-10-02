package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.anthropic.AnthropicChatResponseMetadata;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

/**
 * Prompt caching is a prompt-design concern: the cache matches on an exact prefix
 * (tools, then system, then messages), so stable content goes first and anything that varies
 * per request goes last. {@code cacheSystemMessages(true)} places a cache breakpoint on the
 * system message; the first call writes it, later calls with the same prefix read it at a
 * fraction of the input price. Prefixes below the model's minimum (512 tokens on Sonnet 5.5)
 * are silently not cached.
 *
 * <p>The cache counters are Anthropic-specific, so they sit on subclasses of the generic types:
 * {@link AnthropicTokenUsage} behind {@code Result.tokenUsage()}, and
 * {@link AnthropicChatResponseMetadata} behind the final response's metadata, which also says
 * why a call missed the cache. Spring AI enables caching per call with
 * {@code AnthropicCacheOptions} and exposes the counters on its generic {@code Usage}.
 */
class PromptCachingTest extends BaseTestClass {

    interface ReviewAnalyst {

        @SystemMessage(fromResource = "/prompts/review-analyst.txt")
        Result<String> ask(@V("reviews") String reviews, @UserMessage String question);
    }

    private final ReviewAnalyst analyst = AiServices.create(ReviewAnalyst.class, modelBuilder()
            .cacheSystemMessages(true)
            .returnCacheDiagnostics(true)
            .build());

    @Test
    void stablePrefixIsReadFromCache() {
        var reviews = Reviews.asXml(Reviews.TUMBLER);

        var first = analyst.ask(reviews, "Which complaints should the product team fix first?");
        var second = analyst.ask(reviews, "Which features do satisfied customers mention?");

        print(first);
        print(second);
        assertThat(((AnthropicTokenUsage) second.tokenUsage()).cacheReadInputTokens()).isPositive();
    }

    private void print(Result<String> result) {
        var usage = (AnthropicTokenUsage) result.tokenUsage();
        var diagnostics = ((AnthropicChatResponseMetadata) result.finalResponse().metadata()).cacheDiagnostics();
        System.out.println("input=%d cacheWrite=%s cacheRead=%s cacheMiss=%s%n%s%n".formatted(usage.inputTokenCount(),
                usage.cacheCreationInputTokens(), usage.cacheReadInputTokens(),
                diagnostics == null ? null : diagnostics.cacheMissReasonType(), result.content()));
    }
}
