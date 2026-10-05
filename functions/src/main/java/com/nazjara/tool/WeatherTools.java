package com.nazjara.tool;

import com.nazjara.client.WeatherClient;
import com.nazjara.model.WeatherResponse;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Tools the model may call, declared as annotated methods on an ordinary Spring bean.
 *
 * <p>At startup the LangChain4j Spring Boot starter scans every bean for {@link Tool} methods and
 * hands those beans to each {@code @AiService} (in the default {@code AUTOMATIC} wiring mode). For
 * each method, {@code ToolSpecifications.toolSpecificationFrom(Method)} builds a
 * {@code ToolSpecification}: the tool name (the method name unless {@code @Tool(name = ...)}), its
 * description from {@link Tool#value()}, and a JSON schema whose properties are the method
 * parameters, described by {@link P}. When the model asks for the tool, a
 * {@code DefaultToolExecutor} parses the JSON arguments back into the parameter types and invokes
 * the method by reflection; the return value is serialized to JSON and sent back to the model.
 *
 * <p>The Spring AI counterpart is a {@code FunctionToolCallback} built around a
 * {@code Function<WeatherRequest, WeatherResponse>}, where the schema comes from the input record
 * instead of the method parameters. Spring AI also supports {@code @Tool} methods, but they are
 * passed per call via {@code .tools(...)} rather than discovered globally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherTools {

    private final WeatherClient weatherClient;

    /**
     * Returns the current weather for a city. An exception thrown here does not reach the caller:
     * the AI Service's default {@code ToolExecutionErrorHandler} sends the exception message to the
     * model as the tool result, so the model can apologize or retry with other arguments.
     *
     * @param city city chosen by the model
     * @param country country chosen by the model
     * @return current weather
     */
    @Tool("Get a current weather for a location. Sunrise and sunset are epoch seconds in GMT.")
    public WeatherResponse currentWeather(@P("City name, e.g. Lviv") String city,
                                          @P("Country name, e.g. Ukraine") String country) {
        log.info("Tool currentWeather called with city={}, country={}", city, country);
        return weatherClient.currentWeather(city, country);
    }
}
