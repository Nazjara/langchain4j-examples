package com.nazjara.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;

public record WeatherResponse(@JsonAlias("wind_speed") BigDecimal windSpeed,
                              @JsonAlias("wind_degrees") Integer windDegrees,
                              Integer temp,
                              Integer humidity,
                              Integer sunset,
                              Integer sunrise,
                              @JsonAlias("min_temp") Integer minTemp,
                              @JsonAlias("cloud_pct") Integer cloudPct,
                              @JsonAlias("feels_like") Integer feelsLike,
                              @JsonAlias("max_temp") Integer maxTemp) {
}
