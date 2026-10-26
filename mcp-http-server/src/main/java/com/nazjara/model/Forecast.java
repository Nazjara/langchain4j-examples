package com.nazjara.model;

import java.util.List;

public record Forecast(String place, String timezone, List<DailyForecast> days) {
}
