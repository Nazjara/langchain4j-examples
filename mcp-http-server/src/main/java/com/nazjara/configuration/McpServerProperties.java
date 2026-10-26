package com.nazjara.configuration;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the MCP endpoint ({@code ai.mcp.*}).
 *
 * @param version server version reported in the {@code initialize} response
 * @param endpoint servlet path of the MCP endpoint
 * @param allowedOrigins browser origins allowed to call the endpoint; empty rejects every request
 *                       that carries an {@code Origin} header, while clients that send none (such
 *                       as {@code mcp-agent}) are accepted from any host
 */
@ConfigurationProperties("ai.mcp")
public record McpServerProperties(@DefaultValue("1.0.0") String version,
                                  @DefaultValue("/mcp") String endpoint,
                                  @DefaultValue List<String> allowedOrigins) {
}
