package com.nazjara.service;

import java.util.List;

/** Several places of similar size match the requested name; the caller must narrow it down. */
public class AmbiguousPlaceException extends RuntimeException {

    public AmbiguousPlaceException(String city, List<String> labels) {
        super("Several places named %s match: %s. Ask the user which one they mean, then call again with country and region."
                .formatted(city, String.join("; ", labels)));
    }
}
