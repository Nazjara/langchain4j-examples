package com.nazjara;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reasoning is configured, not prompted. With adaptive thinking the model decides when and how
 * long to think before answering, and {@code effort} sets how much work it puts in overall
 * (thinking depth, length, number of tool calls): the lever for trading quality against cost and
 * latency. "Think step by step" in prompt text is redundant on current models.
 *
 * <p>Both settings live on the model in LangChain4j 1.20.2, so each variant is a separately built
 * model behind the same interface. {@code thinkingDisplay("summarized")} makes the API return the
 * thinking text, and {@code returnThinking(true)} makes LangChain4j keep it on
 * {@code AiMessage.thinking()}. Effort has no typed setter, so it is passed raw through
 * {@code customParameters}, which are merged into the request body. Spring AI sets both per call
 * on {@code AnthropicChatOptions} and returns thinking as separate generations.
 */
class ThinkingAndEffortTest extends BaseTestClass {

    private static final String PROBLEM = "In how many ways can a 3x8 board be tiled with 2x1 dominoes?";

    interface Solver {

        Result<String> solve(String problem);
    }

    @Test
    void adaptiveThinkingExposesAReasoningSummary() {
        var solver = AiServices.create(Solver.class, modelBuilder()
                .thinkingType("adaptive")
                .thinkingDisplay("summarized")
                .returnThinking(true)
                .build());

        var result = solver.solve(PROBLEM);

        System.out.println("[thinking]\n" + result.finalResponse().aiMessage().thinking() + "\n");
        System.out.println("[answer]\n" + result.content());
    }

    @ParameterizedTest
    @ValueSource(strings = { "low", "high" })
    void effortTradesThoroughnessForTokens(String effort) {
        var solver = AiServices.create(Solver.class, modelBuilder()
                .customParameters(Map.of("output_config", Map.of("effort", effort)))
                .build());

        var result = solver.solve(PROBLEM);

        System.out.println("effort=%s outputTokens=%d%n%s".formatted(effort,
                result.tokenUsage().outputTokenCount(), result.content()));
    }
}
