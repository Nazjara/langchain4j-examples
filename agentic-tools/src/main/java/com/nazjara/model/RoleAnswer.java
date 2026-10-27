package com.nazjara.model;

import java.util.List;

public record RoleAnswer(String answer, Role role, int modelCalls, List<ToolCall> toolCalls) {
}
