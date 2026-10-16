package com.nazjara.model;

import java.util.List;

public record AskResponse(String answer,
                          ToolSearch toolSearch,
                          int modelCalls,
                          List<ToolCall> toolCalls,
                          Integer inputTokens,
                          Integer outputTokens,
                          Integer cacheReadInputTokens,
                          Integer cacheCreationInputTokens) {
}
