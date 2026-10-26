package com.nazjara.resource;

import com.nazjara.service.PlaceFinder;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The resources this server publishes: read-only data an application or a model can fetch by URI.
 *
 * <p>A resource has a fixed URI and is listed by {@code resources/list}. A resource template has
 * a URI with {@code {variables}}, is listed by {@code resources/templates/list}, and is read by
 * filling the variables in. Both are read with {@code resources/read}. The SDK first tries an
 * exact URI match, then each template; the handler extracts the variables itself.
 *
 * <p>MCP calls resources <b>application-controlled</b>: the host decides when to read them, unlike
 * tools, which the model calls. The {@code mcp-agent} module shows both sides: its
 * {@code POST /resources/ask} endpoint reads a resource and puts it in the prompt, and its
 * resources-as-tools presenter lets Claude list and read them itself.
 *
 * <p>Resources are read fresh on every request, so the server never needs to notify clients of
 * changes. A stateless server could not send {@code notifications/resources/updated} anyway.
 */
@Component
@RequiredArgsConstructor
public class WeatherResources {

    public static final String WMO_CODES_URI = "weather://wmo-codes";
    private static final String CITY_URI_TEMPLATE = "weather://city/{name}";
    private static final String JSON = "application/json";

    private final PlaceFinder placeFinder;
    private final JsonMapper jsonMapper;

    /**
     * Returns the fixed resources: the WMO weather code table.
     *
     * @return the resource specifications
     */
    public List<SyncResourceSpecification> resources() {
        var resource = Resource.builder(WMO_CODES_URI, "wmo-weather-codes")
                .title("WMO weather codes")
                .description("Meaning of the WMO weather codes returned by the forecast tool, e.g. 61 = slight rain")
                .mimeType(JSON)
                .build();
        return List.of(new SyncResourceSpecification(resource,
                (context, request) -> ReadResourceResult.builder(List.of(wmoCodes())).build()));
    }

    /**
     * Returns the resource templates: the places a city name matches.
     *
     * @return the resource template specifications
     */
    public List<SyncResourceTemplateSpecification> resourceTemplates() {
        var template = ResourceTemplate.builder(CITY_URI_TEMPLATE, "city")
                .title("Places by name")
                .description("All places named exactly {name}, most populous first, with region, country, coordinates, "
                        + "time zone and population; URL-encode the name, e.g. weather://city/San%20Jose")
                .mimeType(JSON)
                .build();
        var uriTemplate = new DefaultMcpUriTemplateManager(CITY_URI_TEMPLATE);
        return List.of(new SyncResourceTemplateSpecification(template, (context, request) -> {
            var name = URLDecoder.decode(uriTemplate.extractVariableValues(request.uri()).get("name"), StandardCharsets.UTF_8);
            var places = placeFinder.exactMatches(name);
            if (places.isEmpty()) {
                throw McpError.RESOURCE_NOT_FOUND.apply(request.uri());
            }
            var contents = TextResourceContents.builder(request.uri(), jsonMapper.writeValueAsString(places)).mimeType(JSON).build();
            return ReadResourceResult.builder(List.of(contents)).build();
        }));
    }

    /**
     * Reads the WMO weather code table. Also embedded by the {@code trip-weather-brief} prompt.
     *
     * @return the table as a JSON object keyed by code
     */
    public TextResourceContents wmoCodes() {
        try {
            var text = new ClassPathResource("mcp/wmo-weather-codes.json").getContentAsString(StandardCharsets.UTF_8);
            return TextResourceContents.builder(WMO_CODES_URI, text).mimeType(JSON).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
