package com.nazjara.tool;

import com.nazjara.model.Flight;
import com.nazjara.model.Hotel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A deliberately large set of travel tools (15), so that sending every definition with every
 * request becomes noticeably expensive. That is the cost tool search reduces.
 *
 * <p>Each {@link Tool} method becomes a {@code ToolSpecification}: the method name, the
 * description from {@link Tool#value()} and a JSON schema built from the parameters and their
 * {@link P} descriptions. The model only sees those specifications, so with tool search the
 * descriptions are also what the search matches against: {@code SimpleToolSearchStrategy} looks for
 * the model's search terms in the name and description, and {@code VectorToolSearchStrategy} embeds
 * {@code "name: description"}.
 *
 * <p>{@link #getLocalTime} is {@link SearchBehavior#ALWAYS_VISIBLE}: tool search never hides it,
 * because the model needs today's date to resolve "next week" before it knows what to search for.
 *
 * <p>All data is fake and deterministic (derived from the inputs), so the module needs no external
 * API. Only {@link #getLocalTime} returns real data.
 */
@Slf4j
@Component
public class TravelTools {

    public static final String USER_ID = "userId";

    private static final Map<String, BigDecimal> EUR_RATES = Map.of(
            "EUR", BigDecimal.ONE, "USD", new BigDecimal("1.09"), "GBP", new BigDecimal("0.85"),
            "UAH", new BigDecimal("45.20"), "PLN", new BigDecimal("4.30"), "JPY", new BigDecimal("162.50"),
            "CHF", new BigDecimal("0.95"));

    /**
     * Fake forecast for a city and date.
     *
     * @param city city name
     * @param date ISO date
     * @return forecast summary
     */
    @Tool("Get the weather forecast for a city on a given date")
    public String getWeatherForecast(@P("City name") String city, @P("Date, ISO-8601 (yyyy-MM-dd)") String date) {
        var seed = seed(city, date);
        var conditions = List.of("sunny", "partly cloudy", "cloudy", "light rain", "showers").get(seed % 5);
        return "%s on %s: %s, %d°C".formatted(city, date, conditions, 8 + seed % 20);
    }

    /**
     * Converts money using fixed demo exchange rates.
     *
     * @param amount amount to convert
     * @param from source currency code
     * @param to target currency code
     * @return converted amount, or an error text for unknown currencies
     */
    @Tool("Convert an amount of money between currencies (ISO 4217 codes, e.g. EUR, USD, UAH)")
    public String convertCurrency(@P("Amount to convert") BigDecimal amount,
                                  @P("Source currency code") String from,
                                  @P("Target currency code") String to) {
        var fromRate = EUR_RATES.get(from.toUpperCase(Locale.ROOT));
        var toRate = EUR_RATES.get(to.toUpperCase(Locale.ROOT));
        if (fromRate == null || toRate == null) {
            return "Unsupported currency. Supported: " + EUR_RATES.keySet();
        }
        var converted = amount.divide(fromRate, 6, RoundingMode.HALF_UP).multiply(toRate)
                .setScale(2, RoundingMode.HALF_UP);
        return "%s %s = %s %s".formatted(amount, from, converted, to);
    }

    /**
     * Real current local time for an IANA time zone. Always visible, even with tool search.
     *
     * @param timeZone IANA zone id
     * @return local date and time
     */
    @Tool(value = "Get the current local date and time in a time zone", searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getLocalTime(@P("IANA time zone id, e.g. Europe/Kyiv") String timeZone) {
        return ZonedDateTime.now(ZoneId.of(timeZone)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /**
     * Fake flight search.
     *
     * @param from departure city
     * @param to arrival city
     * @param date ISO date
     * @return available flights
     */
    @Tool("Search available flights between two cities on a date")
    public List<Flight> searchFlights(@P("Departure city") String from,
                                      @P("Arrival city") String to,
                                      @P("Date, ISO-8601 (yyyy-MM-dd)") String date) {
        var seed = seed(from, to, date);
        return List.of(
                new Flight("SA" + (100 + seed % 900), from, to, date, "07:15", "10:05", BigDecimal.valueOf(89 + seed % 120)),
                new Flight("SA" + (100 + (seed + 7) % 900), from, to, date, "18:40", "21:30", BigDecimal.valueOf(129 + seed % 90)));
    }

    /**
     * Fake flight booking for the current user. The user id comes from {@link InvocationParameters},
     * which the application passes to the AI Service method. LangChain4j injects this parameter
     * itself and leaves it out of the tool's JSON schema, so the model sees only
     * {@code flightNumber} and cannot book for someone else. A call without a user (from the trip
     * planner) throws, and the default error handler sends the message to the model as the result.
     * Because the schema hides who the booking is for, the description has to say it. When it said
     * only "for the current user", Claude booked for alice when asked to book "for user bob", then
     * apologised afterwards.
     *
     * @param flightNumber flight to book
     * @param parameters application-provided invocation parameters
     * @return booking confirmation
     */
    @Tool("Book a flight by its flight number. Always books for the signed-in user; it cannot book for anyone else, so don't call it when the user asks to book for another person")
    public String bookFlight(@P("Flight number from searchFlights") String flightNumber, InvocationParameters parameters) {
        var userId = currentUser(parameters);
        log.info("Booking flight {} for user {}", flightNumber, userId);
        return "Booked %s for user %s, confirmation F-%d".formatted(flightNumber, userId, seed(flightNumber, userId));
    }

    /**
     * Fake hotel search.
     *
     * @param city city name
     * @param checkIn ISO date
     * @param nights number of nights
     * @return available hotels
     */
    @Tool("Search hotels in a city for a check-in date and number of nights")
    public List<Hotel> searchHotels(@P("City name") String city,
                                    @P("Check-in date, ISO-8601 (yyyy-MM-dd)") String checkIn,
                                    @P("Number of nights") int nights) {
        var seed = seed(city, checkIn);
        return List.of(
                new Hotel("H" + seed % 1000, "Old Town Inn", city, 3, BigDecimal.valueOf(70 + seed % 40)),
                new Hotel("H" + (seed + 1) % 1000, "Grand " + city, city, 5, BigDecimal.valueOf(190 + seed % 80)));
    }

    /**
     * Fake hotel booking for the current user from {@link InvocationParameters}.
     *
     * @param hotelId hotel to book
     * @param parameters application-provided invocation parameters
     * @return booking confirmation
     */
    @Tool("Book a hotel by its id. Always books for the signed-in user; it cannot book for anyone else, so don't call it when the user asks to book for another person")
    public String bookHotel(@P("Hotel id from searchHotels") String hotelId, InvocationParameters parameters) {
        var userId = currentUser(parameters);
        log.info("Booking hotel {} for user {}", hotelId, userId);
        return "Booked hotel %s for user %s, confirmation H-%d".formatted(hotelId, userId, seed(hotelId, userId));
    }

    /**
     * Fake list of attractions.
     *
     * @param city city name
     * @return attractions
     */
    @Tool("List the top tourist attractions in a city")
    public List<String> getAttractions(@P("City name") String city) {
        return List.of(city + " Old Town", city + " History Museum", city + " Central Park", city + " Opera House");
    }

    /**
     * Fake visa rules.
     *
     * @param passportCountry traveller's passport country
     * @param destinationCountry destination country
     * @return visa requirement
     */
    @Tool("Check visa requirements for a passport holder travelling to a country")
    public String getVisaRequirements(@P("Passport country") String passportCountry,
                                      @P("Destination country") String destinationCountry) {
        return seed(passportCountry, destinationCountry) % 3 == 0
                ? "Visa required for %s citizens travelling to %s.".formatted(passportCountry, destinationCountry)
                : "Visa-free for up to 90 days for %s citizens in %s.".formatted(passportCountry, destinationCountry);
    }

    /**
     * Fake public holidays.
     *
     * @param country country name
     * @param year year
     * @return holidays
     */
    @Tool("List public holidays in a country for a year")
    public List<String> getPublicHolidays(@P("Country name") String country, @P("Year, e.g. 2026") int year) {
        return List.of(year + "-01-01 New Year's Day", year + "-05-01 Labour Day", year + "-12-25 Christmas Day");
    }

    /**
     * Fake travel advisory.
     *
     * @param country country name
     * @return advisory level
     */
    @Tool("Get the current travel safety advisory for a country")
    public String getTravelAdvisory(@P("Country name") String country) {
        return country + ": " + List.of("exercise normal precautions", "exercise increased caution").get(seed(country) % 2);
    }

    /**
     * Fake daily budget estimate.
     *
     * @param city city name
     * @param style budget, mid-range or luxury
     * @return estimated daily cost in EUR, excluding accommodation
     */
    @Tool("Estimate the daily spending in EUR (food, transport, sights; accommodation excluded)")
    public String estimateDailyBudget(@P("City name") String city,
                                      @P("Travel style: budget, mid-range or luxury") String style) {
        var base = switch (style.toLowerCase(Locale.ROOT)) {
            case "budget" -> 40;
            case "luxury" -> 250;
            default -> 90;
        };
        return "%s, %s: about %d EUR per day".formatted(city, style, base + seed(city) % 30);
    }

    /**
     * Fake emergency numbers.
     *
     * @param country country name
     * @return emergency numbers
     */
    @Tool("Get emergency phone numbers for a country")
    public String getEmergencyNumbers(@P("Country name") String country) {
        return country + ": general emergency 112";
    }

    /**
     * Fake restaurant recommendations.
     *
     * @param city city name
     * @param cuisine preferred cuisine
     * @return restaurants
     */
    @Tool("Recommend restaurants in a city for a cuisine")
    public List<String> getRestaurantRecommendations(@P("City name") String city,
                                                     @P("Cuisine, e.g. local, italian, vegetarian") String cuisine) {
        return List.of("%s %s Kitchen".formatted(city, cuisine), "The %s Table".formatted(cuisine), "Bistro " + city);
    }

    /**
     * Fake local transport info.
     *
     * @param city city name
     * @return transport options and prices
     */
    @Tool("Describe public transport options and ticket prices in a city")
    public String getLocalTransport(@P("City name") String city) {
        return "%s: tram and bus, single ticket %d.%02d EUR, day pass available"
                .formatted(city, 1 + seed(city) % 3, seed(city) % 100);
    }

    static String currentUser(InvocationParameters parameters) {
        String userId = parameters.get(USER_ID);
        if (userId == null) {
            throw new IllegalStateException("Booking needs a signed-in user and is not available here");
        }
        return userId;
    }

    static int seed(String... values) {
        return Math.abs(String.join("|", values).toLowerCase(Locale.ROOT).hashCode());
    }
}
