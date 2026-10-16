package com.nazjara.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits bound from {@code ai.agentic-tools.*}: the round-trip cap shared by all AI Services and
 * the trip planner's memory window and guardrail attempts. None of these have a
 * {@code langchain4j.*} starter property, because the starter cannot configure services built with
 * {@code AiServices.builder(...)}.
 *
 * @param maxToolCallingRoundTrips model calls allowed per request before the proxy throws
 * @param plan                     trip planner settings
 */
@ConfigurationProperties(prefix = "ai.agentic-tools")
public record AgenticToolsProperties(int maxToolCallingRoundTrips, Plan plan) {

    /**
     * @param memoryMessages messages kept in the planner's throwaway chat memory
     * @param maxRetries     guardrail attempts, counting the original reply
     */
    public record Plan(int memoryMessages, int maxRetries) {
    }
}
