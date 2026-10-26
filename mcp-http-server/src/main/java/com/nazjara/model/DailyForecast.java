package com.nazjara.model;

import java.time.LocalDate;

public record DailyForecast(LocalDate date,
                            Integer weatherCode,
                            Double maxTemperature,
                            Double minTemperature,
                            Double precipitation,
                            Double maxWindSpeed) {
}
