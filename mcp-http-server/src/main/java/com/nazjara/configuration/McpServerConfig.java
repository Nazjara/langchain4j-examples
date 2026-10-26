package com.nazjara.configuration;

import com.nazjara.prompt.WeatherPrompts;
import com.nazjara.resource.WeatherResources;
import com.nazjara.tool.WeatherTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.DefaultServerTransportSecurityValidator;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a stateless MCP server over Streamable HTTP with the official MCP Java SDK, as a plain
 * servlet inside Spring Boot. There is no Spring AI and no LangChain4j on this side.
 *
 * <p><b>Why not LangChain4j?</b> {@code langchain4j-community-mcp-server} (the {@code mcp-server}
 * module) has only a stdio transport and serves only tools. An MCP server that other machines can
 * reach, and that serves resources and prompts, needs another library. The SDK is the reference
 * implementation of the protocol, so this module is the counterpart for LangChain4j's MCP client
 * in {@code mcp-agent}, not a LangChain4j feature. Spring AI builds the same server from
 * {@code spring-ai-starter-mcp-server-webmvc} and {@code @McpTool} / {@code @McpResource} /
 * {@code @McpPrompt} beans; {@code spring.ai.mcp.server.protocol=STATELESS} switches it to its own
 * {@code WebMvcStatelessServerTransport}, a Spring MVC counterpart of the servlet used here.
 *
 * <p><b>Stateless.</b> Every {@code POST /mcp} is handled on its own: the server answers
 * {@code initialize} but keeps no session and sends no {@code Mcp-Session-Id}. Any instance can
 * answer any request, so it scales out behind a load balancer and loses nothing on restart. The
 * price: no server-to-client messages. There are no logs or progress during a call, no
 * resource-updated or list-changed notifications, and no elicitation or sampling requests.
 * {@code GET /mcp}, the stream such messages would travel on, returns 405. The capabilities
 * declared below say so: {@code listChanged} and {@code subscribe} are {@code false}.
 *
 * <p><b>Open to the network, no authentication.</b> Boot listens on all interfaces, and the
 * endpoint has no auth because the tools only read public, key-free Open-Meteo data. The
 * {@code Origin} check still blocks browsers: without it, any web page the user opens could call
 * the server through the browser (DNS rebinding), which the MCP specification requires servers to
 * prevent.
 */
@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpServerConfig {

    /**
     * The transport: an {@code HttpServlet} that reads one JSON-RPC message per {@code POST},
     * passes it to the server's handler and writes the response as {@code application/json}. It
     * requires {@code Accept: application/json, text/event-stream}, as the Streamable HTTP
     * specification says, and never streams.
     *
     * @param properties endpoint and allowed origins
     * @return the transport
     */
    @Bean
    HttpServletStatelessServerTransport mcpTransport(McpServerProperties properties) {
        return HttpServletStatelessServerTransport.builder()
                .messageEndpoint(properties.endpoint())
                .httpHeaderValidator(DefaultServerTransportSecurityValidator.builder()
                        .allowedOrigins(properties.allowedOrigins())
                        .build())
                .build();
    }

    /**
     * Registers the transport with Tomcat next to Spring MVC's {@code DispatcherServlet}. The SDK
     * servlet needs nothing from Spring MVC.
     *
     * @param transport the transport servlet
     * @param properties endpoint path
     * @return the servlet registration
     */
    @Bean
    ServletRegistrationBean<HttpServletStatelessServerTransport> mcpServlet(HttpServletStatelessServerTransport transport,
                                                                           McpServerProperties properties) {
        var registration = new ServletRegistrationBean<>(transport, properties.endpoint());
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * The server: declares the capabilities, registers tools, resources and prompts, and installs
     * its request handler on the transport. {@code immediateExecution} runs each handler on the
     * servlet's request thread (a virtual thread here) instead of hopping to Reactor's
     * bounded-elastic pool; the transport blocks that thread until the response is ready anyway.
     *
     * @param transport the transport the server answers on
     * @param weatherTools the {@code forecast} and {@code airQuality} tools
     * @param weatherResources the WMO code table and the city template
     * @param weatherPrompts the {@code trip-weather-brief} prompt
     * @param name server name reported in the {@code initialize} response
     * @param properties server version
     * @return the server, closed with the application context
     */
    @Bean
    McpStatelessSyncServer mcpServer(HttpServletStatelessServerTransport transport,
                                     WeatherTools weatherTools,
                                     WeatherResources weatherResources,
                                     WeatherPrompts weatherPrompts,
                                     @Value("${spring.application.name}") String name,
                                     McpServerProperties properties) {
        return McpServer.sync(transport)
                .serverInfo(name, properties.version())
                .instructions("Weather forecasts and air quality from Open-Meteo. Forecast weather codes are WMO codes; "
                        + "the weather://wmo-codes resource explains them.")
                .capabilities(ServerCapabilities.builder()
                        .tools(false)
                        .resources(false, false)
                        .prompts(false)
                        .build())
                .tools(weatherTools.specifications())
                .resources(weatherResources.resources())
                .resourceTemplates(weatherResources.resourceTemplates())
                .prompts(weatherPrompts.specifications())
                .immediateExecution(true)
                .build();
    }
}
