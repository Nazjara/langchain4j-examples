package com.nazjara.model;

import dev.langchain4j.model.output.structured.Description;

public record CapitalDetails(
        @Description("The country name") String country,
        @Description("The capital city name") String city,
        @Description("The city population") long population,
        @Description("The region of the country the city is located in") String region,
        @Description("The primary language spoken in the city") String language,
        @Description("The currency used, as an ISO 4217 code") String currency) {
}
