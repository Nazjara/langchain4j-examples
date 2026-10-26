package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nazjara.client.OpenMeteoClient;
import com.nazjara.model.AirQuality;
import com.nazjara.model.DailyForecast;
import com.nazjara.model.Place;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * LangChain4j's MCP client ({@code langchain4j-mcp}, as used by {@code mcp-agent}) against this
 * server over real Streamable HTTP, with Open-Meteo mocked. No API key needed.
 *
 * <p>This is the check the stateless design depended on: the client works with a server that
 * never sends {@code Mcp-Session-Id} and rejects the optional GET stream.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LangChain4jClientOverHttpTest {

    private static final Place LVIV = new Place("Lviv", "Lviv Oblast", "Ukraine", "UA", 49.84, 24.02, "Europe/Kyiv", 717_803L);
    private static final Place SPRINGFIELD_IL = new Place("Springfield", "Illinois", "United States", "US", 39.8, -89.6, "America/Chicago", 116_250L);
    private static final Place SPRINGFIELD_MO = new Place("Springfield", "Missouri", "United States", "US", 37.2, -93.3, "America/Chicago", 166_810L);

    @LocalServerPort
    private int port;

    @MockitoBean
    private OpenMeteoClient openMeteoClient;

    private StreamableHttpMcpTransport transport;
    private McpClient mcpClient;

    @BeforeEach
    void setUp() {
        transport = StreamableHttpMcpTransport.builder().url(endpoint()).build();
        mcpClient = DefaultMcpClient.builder().key("open-meteo").transport(transport).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        mcpClient.close();
    }

    @Test
    void clientInitializesWithoutASession() {
        assertThat(transport.getMcpSessionId()).isNull();
        assertThatCode(mcpClient::checkHealth).doesNotThrowAnyException();
    }

    @Test
    void listToolsReturnsBothToolsWithTitlesHintsAndRequiredArguments() {
        var tools = mcpClient.listTools();

        assertThat(tools).extracting(ToolSpecification::name).containsExactly("forecast", "airQuality");
        assertThat(tools.getFirst().parameters().required()).containsExactly("city", "days");
        assertThat(tools.getFirst().metadata())
                .containsEntry("title", "Weather forecast")
                .containsEntry("readOnlyHint", true)
                .containsEntry("openWorldHint", true);
    }

    @Test
    void forecastToolReturnsTheChosenPlaceAndDays() {
        when(openMeteoClient.findPlaces("Lviv")).thenReturn(List.of(LVIV));
        when(openMeteoClient.forecast(LVIV, 1)).thenReturn(List.of(new DailyForecast(LocalDate.of(2026, 10, 23), 61, 12.4, 7.5, 1.3, 22.0)));

        var result = mcpClient.executeTool(toolRequest("forecast", "{\"city\":\"Lviv\",\"days\":1}"));

        assertThat(result.isError()).isFalse();
        assertThat(result.resultText())
                .contains("\"place\":\"Lviv, Lviv Oblast, Ukraine\"")
                .contains("\"date\":\"2026-10-23\"")
                .contains("\"weatherCode\":61");
    }

    @Test
    void ambiguousCityComesBackAsAToolErrorListingTheChoices() {
        when(openMeteoClient.findPlaces("Springfield")).thenReturn(List.of(SPRINGFIELD_IL, SPRINGFIELD_MO));

        assertThatThrownBy(() -> mcpClient.executeTool(toolRequest("airQuality", "{\"city\":\"Springfield\"}")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Springfield, Missouri, United States; Springfield, Illinois, United States");
    }

    @Test
    void argumentsOutsideTheSchemaAreRejectedBeforeTheHandlerRuns() {
        assertThatThrownBy(() -> mcpClient.executeTool(toolRequest("forecast", "{\"city\":\"Lviv\",\"days\":40}")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("must have a maximum value of 16");
    }

    @Test
    void airQualityToolReturnsTheCurrentIndex() {
        when(openMeteoClient.findPlaces("Lviv")).thenReturn(List.of(LVIV));
        when(openMeteoClient.airQuality(LVIV)).thenReturn(new AirQuality(LVIV.label(), "2026-10-23T13:00", 23, 6.6, 9.7));

        var result = mcpClient.executeTool(toolRequest("airQuality", "{\"city\":\"Lviv\",\"country\":\"UA\"}"));

        assertThat(result.resultText()).contains("\"europeanAqi\":23");
    }

    @Test
    void resourcesListTheCodeTableAndTheCityTemplate() {
        assertThat(mcpClient.listResources()).extracting(McpResource::uri).containsExactly("weather://wmo-codes");
        assertThat(mcpClient.listResourceTemplates()).singleElement()
                .satisfies(template -> assertThat(template.uriTemplate()).isEqualTo("weather://city/{name}"));
    }

    @Test
    void readingTheCodeTableReturnsJson() {
        var contents = mcpClient.readResource("weather://wmo-codes").contents();

        assertThat(contents).singleElement().isInstanceOfSatisfying(McpTextResourceContents.class, text -> {
            assertThat(text.mimeType()).isEqualTo("application/json");
            assertThat(text.text()).contains("\"61\": \"Slight rain\"");
        });
    }

    @Test
    void readingAFilledTemplateDecodesTheNameAndListsThePlaces() {
        var sanJose = new Place("San Jose", "California", "United States", "US", 37.3, -121.9, "America/Los_Angeles", 1_013_240L);
        when(openMeteoClient.findPlaces("San Jose")).thenReturn(List.of(sanJose));

        var contents = mcpClient.readResource("weather://city/San%20Jose").contents();

        assertThat(contents).singleElement().isInstanceOfSatisfying(McpTextResourceContents.class,
                text -> assertThat(text.text()).contains("\"admin1\":\"California\""));
    }

    @Test
    void readingATemplateWithNoMatchIsResourceNotFound() {
        when(openMeteoClient.findPlaces("Atlantis")).thenReturn(List.of());

        assertThatThrownBy(() -> mcpClient.readResource("weather://city/Atlantis"))
                .isInstanceOfSatisfying(McpException.class, exception -> assertThat(exception.errorCode()).isEqualTo(-32002));
    }

    @Test
    void promptRendersTheForecastAndEmbedsTheCodeTable() {
        var startDate = LocalDate.now(ZoneId.of(LVIV.timezone())).plusDays(1);
        when(openMeteoClient.findPlaces("Lviv")).thenReturn(List.of(LVIV));
        when(openMeteoClient.forecast(eq(LVIV), eq(startDate), eq(startDate.plusDays(1))))
                .thenReturn(List.of(new DailyForecast(startDate, 3, 14.6, 5.9, 0.0, 9.4)));

        var prompt = mcpClient.getPrompt("trip-weather-brief", Map.of("city", "Lviv", "startDate", startDate.toString(), "days", "2"));

        var messages = prompt.messages().stream().map(McpPromptMessage::toChatMessage).toList();
        assertThat(messages).hasSize(2).allSatisfy(message -> assertThat(message).isInstanceOf(UserMessage.class));
        assertThat(((UserMessage) messages.get(0)).singleText()).contains("Lviv, Lviv Oblast, Ukraine").contains("\"weatherCode\":3");
        assertThat(((UserMessage) messages.get(1)).singleText()).contains("\"3\": \"Overcast\"");
    }

    @Test
    void promptWithABadArgumentIsInvalidParams() {
        when(openMeteoClient.findPlaces("Lviv")).thenReturn(List.of(LVIV));

        assertThatThrownBy(() -> mcpClient.getPrompt("trip-weather-brief", Map.of("city", "Lviv", "startDate", "2026-10-25", "days", "40")))
                .isInstanceOfSatisfying(McpException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(-32602);
                    assertThat(exception.errorMessage()).contains("days must be a whole number from 1 to 16");
                });
        assertThat(mcpClient.listPrompts()).singleElement().satisfies(prompt -> assertThat(prompt.name()).isEqualTo("trip-weather-brief"));
    }

    @Test
    void rejectedGetStreamDoesNotBreakAClientThatAsksForIt() throws Exception {
        var streamingTransport = StreamableHttpMcpTransport.builder().url(endpoint()).subsidiaryChannel(true).build();
        try (var streamingClient = DefaultMcpClient.builder().transport(streamingTransport).build()) {
            assertThat(streamingClient.listTools()).hasSize(2);
        }
    }

    @Test
    void browserRequestsAreRejectedByTheOriginCheck() throws Exception {
        try (var httpClient = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(endpoint()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header("Origin", "http://evil.example")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    private String endpoint() {
        return "http://localhost:" + port + "/mcp";
    }

    private static ToolExecutionRequest toolRequest(String name, String arguments) {
        return ToolExecutionRequest.builder().id("call-1").name(name).arguments(arguments).build();
    }
}
