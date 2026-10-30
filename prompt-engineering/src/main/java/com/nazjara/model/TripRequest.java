package com.nazjara.model;

import java.util.List;
import dev.langchain4j.model.input.structured.StructuredPrompt;

@StructuredPrompt({
        "Plan a {{days}}-day trip to {{city}}.",
        "Interests: {{interests}}.",
        "Traveller: {{traveller}}.",
        "Give one short paragraph per day."
})
public record TripRequest(String city, int days, List<String> interests, Traveller traveller) {
}
