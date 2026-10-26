package com.nazjara.service;

import com.nazjara.client.OpenMeteoClient;
import com.nazjara.model.Place;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns a city name chosen by the model into one Open-Meteo place.
 *
 * <p>Geocoding returns fuzzy matches and same-name villages, so only exact name matches are kept,
 * then narrowed by the optional country and region. A place at least 10 times more populous than the next one wins without asking ("Lviv" is the city, not the two
 * villages of that name). Otherwise the choice is a question for the user.
 *
 * <p>A stateful server could ask that question itself with an MCP elicitation request, as the
 * Spring AI server in {@code spring-ai-examples} does. This server is stateless and LangChain4j's
 * client cannot answer elicitation anyway, so the choices travel back in an error result: Claude
 * reads them, asks the user, and the user's next request names the place.
 */
@Service
@RequiredArgsConstructor
public class PlaceFinder {

    private static final int MAX_CHOICES = 5;
    private static final long DOMINANT_RATIO = 10;

    private final OpenMeteoClient openMeteoClient;

    /**
     * Lists the places whose name is exactly {@code name}, most populous first.
     *
     * @param name place name
     * @return exact matches, possibly empty
     */
    public List<Place> exactMatches(String name) {
        return openMeteoClient.findPlaces(name).stream()
                .filter(place -> place.name().equalsIgnoreCase(name))
                .sorted(Comparator.comparingLong(Place::populationOrZero).reversed())
                .toList();
    }

    /**
     * Chooses one place for a city name.
     *
     * @param city city name
     * @param country optional country name or ISO code
     * @param region optional region or state
     * @return the chosen place
     * @throws IllegalArgumentException if no place matches
     * @throws AmbiguousPlaceException if several places of similar size match
     */
    public Place choose(String city, String country, String region) {
        var matches = exactMatches(city).stream()
                .filter(place -> isBlank(country)
                        || country.equalsIgnoreCase(place.country()) || country.equalsIgnoreCase(place.countryCode()))
                .filter(place -> isBlank(region) || region.equalsIgnoreCase(place.admin1()))
                .limit(MAX_CHOICES)
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("No place named " + city + " was found");
        }
        if (matches.size() == 1
                || matches.get(0).populationOrZero() >= DOMINANT_RATIO * matches.get(1).populationOrZero()) {
            return matches.getFirst();
        }
        throw new AmbiguousPlaceException(city, matches.stream().map(Place::label).toList());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
