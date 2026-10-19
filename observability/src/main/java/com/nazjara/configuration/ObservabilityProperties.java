package com.nazjara.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings bound from {@code ai.observability.*}.
 *
 * @param retrievalMaxResults chunks the content retriever returns per question
 * @param includeContent      whether spans carry the question, answer, retrieval query and tool
 *                            arguments/results. Useful while learning; it is user data, so keep it
 *                            {@code false} in production. Spring AI's counterparts are the
 *                            {@code spring.ai.*.observations.log-*} / {@code include-content}
 *                            properties.
 */
@ConfigurationProperties(prefix = "ai.observability")
public record ObservabilityProperties(int retrievalMaxResults, boolean includeContent) {
}
