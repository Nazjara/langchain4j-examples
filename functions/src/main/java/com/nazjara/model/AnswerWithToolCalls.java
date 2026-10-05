package com.nazjara.model;

import java.util.List;

public record AnswerWithToolCalls(String answer, List<ToolCall> toolCalls) {
}
