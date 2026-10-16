package com.nazjara.model;

import dev.langchain4j.model.output.structured.Description;
import java.math.BigDecimal;
import java.util.List;

public record TripPlan(
        @Description("Destination city") String destination,
        @Description("Trip start date, ISO-8601 (yyyy-MM-dd)") String startDate,
        @Description("One entry per day of the trip") List<DayPlan> days,
        @Description("Items to pack, based on the forecast") List<String> packingList,
        @Description("Estimated total cost in EUR, flights and hotel included") BigDecimal estimatedBudgetEur) {

    public record DayPlan(
            @Description("Day number, starting at 1") int day,
            @Description("Weather summary for the day") String weather,
            @Description("Planned activities") List<String> activities) {
    }
}
