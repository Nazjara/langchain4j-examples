package com.nazjara.model;

import java.util.List;

public record SkillsAnswer(String answer,
                           List<String> activatedSkills,
                           int modelCalls,
                           List<ToolCall> toolCalls,
                           Integer inputTokens,
                           Integer outputTokens,
                           Integer cacheReadInputTokens,
                           Integer cacheCreationInputTokens) {
}
