package com.nazjara.tool;

import com.nazjara.client.WeatherClient;
import com.nazjara.model.WeatherResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The tool this server publishes over MCP, declared with the same {@link Tool} annotation an AI
 * Service uses for local tools.
 *
 * <p>LangChain4j has no MCP-specific tool annotation. {@code McpServer} takes any object with
 * {@link Tool} methods and builds a {@code ToolSpecification} and a {@code DefaultToolExecutor} for
 * each, exactly as an AI Service does. The specification becomes the {@code tools/list} entry (name,
 * description, {@code inputSchema}); the executor runs on {@code tools/call}.
 *
 * <p>{@link Tool#metadata()} is a JSON object that {@code McpToolSchemaMapper} splits three ways:
 * {@code title} becomes the tool's title, the four {@code *Hint} keys go under {@code annotations},
 * and any other key goes under {@code _meta}. The hints tell a client whether the tool is safe to
 * run without asking the user first.
 *
 * <p>The Spring AI counterpart is {@code @McpTool} with {@code @McpTool.McpAnnotations}. Its tools
 * can also take an {@code McpSyncRequestContext} parameter to send log or progress notifications
 * back to the client while they run; LangChain4j's server has no such channel.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTools {

    private final WeatherClient weatherClient;

    /**
     * Returns the current weather for a city. The result is serialized to JSON and returned as one
     * text content block; an exception becomes a {@code tools/call} result with
     * {@code isError: true}, not a JSON-RPC error.
     *
     * @param city city chosen by the model
     * @param country country chosen by the model
     * @return current weather
     */
    @Tool(value = "Get the current weather for a city. Sunrise and sunset are epoch seconds in GMT.",
            metadata = """
                    {"title": "Current weather", "readOnlyHint": true, "destructiveHint": false, "idempotentHint": true}""")
    public WeatherResponse currentWeather(@P("City name, e.g. Lviv") String city,
                                          @P("Country name, e.g. Ukraine") String country) {
        log.info("Tool currentWeather called with city={}, country={}", city, country);
        return weatherClient.currentWeather(city, country);
    }
}
