package com.nazjara.model;

import java.util.List;

public record AnswerWithSources(String answer, List<Source> sources, String traceId) {
}
