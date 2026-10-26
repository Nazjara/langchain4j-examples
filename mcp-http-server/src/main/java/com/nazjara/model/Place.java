package com.nazjara.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Place(String name,
                    String admin1,
                    String country,
                    @JsonAlias("country_code") String countryCode,
                    double latitude,
                    double longitude,
                    String timezone,
                    Long population) {

    public String label() {
        return Stream.of(name, admin1, country)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    public long populationOrZero() {
        return population == null ? 0 : population;
    }
}
