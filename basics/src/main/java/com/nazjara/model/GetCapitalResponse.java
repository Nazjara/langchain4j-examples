package com.nazjara.model;

import dev.langchain4j.model.output.structured.Description;

public record GetCapitalResponse(@Description("The capital city name") String answer) {
}
