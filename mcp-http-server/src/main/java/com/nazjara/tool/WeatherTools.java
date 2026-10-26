package com.nazjara.tool;

import com.nazjara.client.OpenMeteoClient;
import com.nazjara.model.Forecast;
import com.nazjara.service.PlaceFinder;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The two tools this server publishes, written against the MCP Java SDK.
 *
 * <p>LangChain4j's own server builds a tool from a {@code @Tool} method. The SDK has no
 * annotations: a tool is a {@link Tool} descriptor (name, description, JSON input schema, hints)
 * plus a handler function, paired in a {@link SyncToolSpecification}. On {@code tools/list} the
 * server returns the descriptors. On {@code tools/call} it first validates the arguments against
 * the input schema: invalid arguments come back as an error result and never reach the handler.
 *
 * <p>The handlers are stateless: they get the request and an {@code McpTransportContext} (HTTP
 * request data), not a session exchange. So they can't send progress or log messages while they
 * run, and they can't ask the client anything.
 *
 * <p>Errors are returned as a result with {@code isError: true}, not thrown. MCP reserves JSON-RPC
 * errors (which a thrown exception becomes) for protocol problems such as an unknown tool, and
 * some clients never show them to the model. An error result is meant for the model, so it can
 * react, e.g. ask which of several places the user means. LangChain4j's client turns either kind
 * into a {@code ToolExecutionException}, and the AI Service passes its message to Claude as the
 * tool result.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTools {

    private static final int MAX_DAYS = 16;
    private static final ToolAnnotations READ_ONLY = ToolAnnotations.builder()
            .readOnlyHint(true)
            .destructiveHint(false)
            .idempotentHint(true)
            .openWorldHint(true)
            .build();
    private static final Map<String, Object> CITY = Map.of("type", "string", "description", "City name, e.g. Lviv");
    private static final Map<String, Object> COUNTRY = Map.of("type", "string",
            "description", "Optional country name or ISO code, e.g. Ukraine or UA; narrows the search");
    private static final Map<String, Object> REGION = Map.of("type", "string",
            "description", "Optional region or state, e.g. Lviv Oblast; narrows the search");

    private final OpenMeteoClient openMeteoClient;
    private final PlaceFinder placeFinder;
    private final JsonMapper jsonMapper;

    /**
     * Returns the specifications of all tools, ready to be registered on the server.
     *
     * @return the {@code forecast} and {@code airQuality} tools
     */
    public List<SyncToolSpecification> specifications() {
        return List.of(forecastTool(), airQualityTool());
    }

    private SyncToolSpecification forecastTool() {
        var tool = Tool.builder("forecast", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "city", CITY,
                                "country", COUNTRY,
                                "region", REGION,
                                "days", Map.of("type", "integer", "minimum", 1, "maximum", MAX_DAYS,
                                        "description", "Number of days, starting today")),
                        "required", List.of("city", "days")))
                .title("Weather forecast")
                .description("Daily weather forecast for a city for 1 to 16 days from today, in the city's local time zone: "
                        + "WMO weather code, max and min temperature in °C, precipitation in mm, max wind speed in km/h. "
                        + "If several places of similar size share the name, the result lists them so the user can choose.")
                .annotations(READ_ONLY)
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> call(request, arguments -> {
                    var place = placeFinder.choose(string(arguments, "city"), string(arguments, "country"), string(arguments, "region"));
                    var days = ((Number) arguments.get("days")).intValue();
                    return new Forecast(place.label(), place.timezone(), openMeteoClient.forecast(place, days));
                }))
                .build();
    }

    private SyncToolSpecification airQualityTool() {
        var tool = Tool.builder("airQuality", Map.of(
                        "type", "object",
                        "properties", Map.of("city", CITY, "country", COUNTRY, "region", REGION),
                        "required", List.of("city")))
                .title("Air quality")
                .description("Current air quality for a city: European AQI (0-20 good, 20-40 fair, 40-60 moderate, "
                        + "60-80 poor, 80-100 very poor, above 100 extremely poor), PM2.5 and PM10 in μg/m³. "
                        + "If several places of similar size share the name, the result lists them so the user can choose.")
                .annotations(READ_ONLY)
                .build();
        return SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((context, request) -> call(request, arguments -> openMeteoClient.airQuality(
                        placeFinder.choose(string(arguments, "city"), string(arguments, "country"), string(arguments, "region")))))
                .build();
    }

    private CallToolResult call(CallToolRequest request, Function<Map<String, Object>, Object> handler) {
        log.info("Tool {} called with {}", request.name(), request.arguments());
        try {
            var result = handler.apply(request.arguments());
            return CallToolResult.builder().addTextContent(jsonMapper.writeValueAsString(result)).build();
        } catch (RuntimeException e) {
            log.warn("Tool {} failed: {}", request.name(), e.getMessage());
            return CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
        }
    }

    private static String string(Map<String, Object> arguments, String name) {
        return arguments.get(name) instanceof String value ? value : null;
    }
}
