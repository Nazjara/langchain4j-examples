package com.nazjara.model;

public record AnswerWithMetadata(String answer, Integer inputTokens, Integer outputTokens, String finishReason) {
}
