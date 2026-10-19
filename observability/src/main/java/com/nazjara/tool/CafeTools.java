package com.nazjara.tool;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Live data that the static knowledge base does not contain, so the model has a reason to call a
 * tool, and the trace shows an {@code execute_tool} span between two chat spans.
 */
@Component
public class CafeTools {

    private static final ZoneId CAFE_ZONE = ZoneId.of("Europe/Kyiv");
    private static final Map<DayOfWeek, String> SPECIALS = Map.of(
            DayOfWeek.MONDAY, "borscht with pampushky",
            DayOfWeek.TUESDAY, "mushroom banosh",
            DayOfWeek.WEDNESDAY, "potato varenyky",
            DayOfWeek.THURSDAY, "cheese syrnyky",
            DayOfWeek.FRIDAY, "apple strudel",
            DayOfWeek.SATURDAY, "Lviv cheesecake",
            DayOfWeek.SUNDAY, "honey cake");

    /**
     * Today's special in the café's time zone (fake data, keyed by day of week).
     *
     * @return today's special dish
     */
    @Tool("Get today's special dish at Spring Brew Café")
    public String getTodaysSpecial() {
        var today = LocalDate.now(CAFE_ZONE).getDayOfWeek();
        return "Today's special (" + today + ") is " + SPECIALS.get(today) + ".";
    }
}
