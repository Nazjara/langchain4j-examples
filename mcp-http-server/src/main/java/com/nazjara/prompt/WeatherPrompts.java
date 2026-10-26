package com.nazjara.prompt;

import com.nazjara.client.OpenMeteoClient;
import com.nazjara.model.Place;
import com.nazjara.resource.WeatherResources;
import com.nazjara.service.PlaceFinder;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncPromptSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.Prompt;
import io.modelcontextprotocol.spec.McpSchema.PromptArgument;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The prompt template this server publishes: {@code trip-weather-brief}.
 *
 * <p>MCP prompts are <b>user-controlled</b>: a client lists them ({@code prompts/list}), the user
 * picks one and fills its arguments, and {@code prompts/get} returns ready-to-send chat messages.
 * The server does the rendering, so it can put data into the messages. This one geocodes the
 * city, fetches the forecast for the trip dates and embeds the WMO code table as an
 * {@link EmbeddedResource}. The model gets everything it needs in the messages and calls no tool,
 * which matters because {@code mcp-agent} sends rendered prompts straight to the chat model.
 *
 * <p>Arguments are always strings in MCP prompts. A bad argument is a JSON-RPC
 * {@code invalid params} error, which {@code mcp-agent} reports as 502.
 */
@Component
@RequiredArgsConstructor
public class WeatherPrompts {

    private static final int FORECAST_RANGE_DAYS = 16;

    private final OpenMeteoClient openMeteoClient;
    private final PlaceFinder placeFinder;
    private final WeatherResources weatherResources;
    private final JsonMapper jsonMapper;

    /**
     * Returns the prompt specifications, ready to be registered on the server.
     *
     * @return the {@code trip-weather-brief} prompt
     */
    public List<SyncPromptSpecification> specifications() {
        var prompt = Prompt.builder("trip-weather-brief")
                .title("Trip weather brief")
                .description("Day-by-day weather and a packing list for a trip in the next 16 days")
                .arguments(List.of(
                        PromptArgument.builder("city").description("Destination city, e.g. Lviv").required(true).build(),
                        PromptArgument.builder("startDate").description("First day of the trip, ISO date, e.g. 2026-10-25").required(true).build(),
                        PromptArgument.builder("days").description("Trip length in days, 1 to 16").required(true).build(),
                        PromptArgument.builder("country").description("Optional country, when the city name is ambiguous").required(false).build()))
                .build();
        return List.of(new SyncPromptSpecification(prompt, (context, request) -> tripWeatherBrief(request)));
    }

    private GetPromptResult tripWeatherBrief(GetPromptRequest request) {
        var place = choosePlace(argument(request, "city"), optionalArgument(request, "country"));
        var startDate = parseDate(argument(request, "startDate"));
        var days = parseDays(argument(request, "days"));
        var endDate = startDate.plusDays(days - 1);
        var today = LocalDate.now(ZoneId.of(place.timezone()));
        if (startDate.isBefore(today) || endDate.isAfter(today.plusDays(FORECAST_RANGE_DAYS - 1))) {
            throw invalidParams("The trip must fall between %s and %s, the forecast range for %s"
                    .formatted(today, today.plusDays(FORECAST_RANGE_DAYS - 1), place.label()));
        }
        var forecast = openMeteoClient.forecast(place, startDate, endDate);

        var instruction = """
                I am travelling to %s from %s to %s. Write a short weather brief: one line per day with the \
                conditions in words, the high and low in °C, rain and wind, then a packing list of at most five items. \
                The daily forecast below is in the local time zone (%s); temperatures are in °C, precipitation in mm, \
                wind in km/h, and weather codes are WMO codes, explained in the attached table.

                %s""".formatted(place.label(), startDate, endDate, place.timezone(), jsonMapper.writeValueAsString(forecast));
        return GetPromptResult.builder(List.of(
                        PromptMessage.builder(Role.USER, TextContent.builder(instruction).build()).build(),
                        PromptMessage.builder(Role.USER, EmbeddedResource.builder(weatherResources.wmoCodes()).build()).build()))
                .description("Trip weather brief for " + place.label())
                .build();
    }

    private Place choosePlace(String city, String country) {
        try {
            return placeFinder.choose(city, country, null);
        } catch (RuntimeException e) {
            throw invalidParams(e.getMessage());
        }
    }

    private static String argument(GetPromptRequest request, String name) {
        var value = optionalArgument(request, name);
        if (value == null) {
            throw invalidParams("Missing argument " + name);
        }
        return value;
    }

    private static String optionalArgument(GetPromptRequest request, String name) {
        return request.arguments() != null && request.arguments().get(name) instanceof String value && !value.isBlank()
                ? value
                : null;
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalidParams("startDate must be an ISO date such as 2026-10-25, got " + value);
        }
    }

    private static int parseDays(String value) {
        var days = value.matches("\\d{1,2}") ? Integer.parseInt(value) : 0;
        if (days < 1 || days > FORECAST_RANGE_DAYS) {
            throw invalidParams("days must be a whole number from 1 to " + FORECAST_RANGE_DAYS + ", got " + value);
        }
        return days;
    }

    private static McpError invalidParams(String message) {
        return McpError.builder(ErrorCodes.INVALID_PARAMS).message(message).build();
    }
}
