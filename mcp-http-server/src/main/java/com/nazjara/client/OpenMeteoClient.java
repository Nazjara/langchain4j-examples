package com.nazjara.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nazjara.model.AirQuality;
import com.nazjara.model.DailyForecast;
import com.nazjara.model.Place;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the Open-Meteo geocoding, forecast and air-quality APIs, which need no API key.
 *
 * <p>Plain Spring code with no MCP dependency: the classes in {@code tool}, {@code resource} and
 * {@code prompt} are what publish its data over MCP.
 */
@Component
public class OpenMeteoClient {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final String AIR_QUALITY_URL = "https://air-quality-api.open-meteo.com/v1/air-quality";
    private static final String DAILY_FIELDS = "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max";
    private static final String AIR_QUALITY_FIELDS = "european_aqi,pm2_5,pm10";
    private static final int MAX_PLACES = 10;

    private final RestClient restClient = RestClient.create();

    /**
     * Finds places by name, most relevant first. Open-Meteo also returns fuzzy matches, e.g.
     * "Springfield Gardens" for "Springfield".
     *
     * @param name place name
     * @return matching places, possibly empty
     */
    public List<Place> findPlaces(String name) {
        var response = restClient.get()
                .uri(GEOCODING_URL + "?name={name}&count={count}&language=en&format=json", name, MAX_PLACES)
                .retrieve()
                .body(GeocodingResponse.class);
        return Optional.ofNullable(response).map(GeocodingResponse::results).orElse(List.of());
    }

    /**
     * Fetches the daily forecast for a place from today, in the place's local time zone.
     *
     * @param place place to forecast
     * @param days number of days, starting today
     * @return one entry per day
     */
    public List<DailyForecast> forecast(Place place, int days) {
        var response = restClient.get()
                .uri(FORECAST_URL + "?latitude={latitude}&longitude={longitude}&daily={daily}&timezone=auto&forecast_days={days}",
                        place.latitude(), place.longitude(), DAILY_FIELDS, days)
                .retrieve()
                .body(ForecastResponse.class);
        return toDailyForecasts(response);
    }

    /**
     * Fetches the daily forecast for a place between two dates, both included. Open-Meteo answers
     * 400 for dates outside its forecast range (about 16 days ahead).
     *
     * @param place place to forecast
     * @param startDate first day
     * @param endDate last day
     * @return one entry per day
     */
    public List<DailyForecast> forecast(Place place, LocalDate startDate, LocalDate endDate) {
        var response = restClient.get()
                .uri(FORECAST_URL + "?latitude={latitude}&longitude={longitude}&daily={daily}&timezone=auto&start_date={start}&end_date={end}",
                        place.latitude(), place.longitude(), DAILY_FIELDS, startDate, endDate)
                .retrieve()
                .body(ForecastResponse.class);
        return toDailyForecasts(response);
    }

    /**
     * Fetches the current air quality for a place.
     *
     * @param place place to measure
     * @return European AQI, PM2.5 and PM10 for the current hour
     */
    public AirQuality airQuality(Place place) {
        var response = restClient.get()
                .uri(AIR_QUALITY_URL + "?latitude={latitude}&longitude={longitude}&current={current}&timezone=auto",
                        place.latitude(), place.longitude(), AIR_QUALITY_FIELDS)
                .retrieve()
                .body(AirQualityResponse.class);
        if (response == null || response.current() == null) {
            throw new IllegalStateException("Open-Meteo returned no air quality for " + place.label());
        }
        var current = response.current();
        return new AirQuality(place.label(), current.time(), current.europeanAqi(), current.pm25(), current.pm10());
    }

    private static List<DailyForecast> toDailyForecasts(ForecastResponse response) {
        if (response == null || response.daily() == null) {
            return List.of();
        }
        var daily = response.daily();
        return IntStream.range(0, daily.time().size())
                .mapToObj(day -> new DailyForecast(LocalDate.parse(daily.time().get(day)), daily.weatherCode().get(day),
                        daily.maxTemperature().get(day), daily.minTemperature().get(day), daily.precipitation().get(day),
                        daily.maxWindSpeed().get(day)))
                .toList();
    }

    record GeocodingResponse(List<Place> results) {
    }

    record ForecastResponse(Daily daily) {
    }

    record Daily(List<String> time,
                 @JsonProperty("weather_code") List<Integer> weatherCode,
                 @JsonProperty("temperature_2m_max") List<Double> maxTemperature,
                 @JsonProperty("temperature_2m_min") List<Double> minTemperature,
                 @JsonProperty("precipitation_sum") List<Double> precipitation,
                 @JsonProperty("wind_speed_10m_max") List<Double> maxWindSpeed) {
    }

    record AirQualityResponse(Current current) {
    }

    record Current(String time,
                   @JsonProperty("european_aqi") Integer europeanAqi,
                   @JsonProperty("pm2_5") Double pm25,
                   @JsonProperty("pm10") Double pm10) {
    }
}
