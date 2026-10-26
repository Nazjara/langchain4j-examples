package com.nazjara.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.exception.ToolExecutionException;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import dev.langchain4j.mcp.resourcesastools.McpResourcesAsToolsPresenter;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Presents MCP resources to the model as two tools, {@code list_resources} and
 * {@code get_resource}, skipping servers that serve no resources.
 *
 * <p>{@code McpToolProvider} adds the two tools when a presenter is set. Resources are
 * application-controlled in MCP, so without them the model can't see or read a resource;
 * with them, Claude can find the WMO code table and read it before turning a forecast's weather
 * codes into words. {@code get_resource} takes the server key and the URI from the list, and
 * a URI filled in from a template such as {@code weather://city/{name}} works too.
 *
 * <p><b>Why not just {@link DefaultMcpResourcesAsToolsPresenter}?</b> Its {@code list_resources}
 * asks every client and fails as a whole when one of them fails. The stdio {@code mcp-server}
 * answers {@code resources/list} with "Method not found" ({@code -32601}), so with both servers
 * connected the tool would never work. This presenter keeps the default's tool names,
 * descriptions and {@code get_resource}, and replaces only the listing: a server that answers
 * {@code -32601} is skipped, while any other error still fails the tool. That holds for any mix
 * of third-party servers, not just these two. Checking the server's declared capabilities would
 * be cleaner, but {@code McpClient} does not expose them.
 */
@Slf4j
public class SkippingResourcesAsToolsPresenter implements McpResourcesAsToolsPresenter {

    private static final int METHOD_NOT_FOUND = -32601;

    private final McpResourcesAsToolsPresenter defaults = DefaultMcpResourcesAsToolsPresenter.builder().build();
    private final JsonMapper jsonMapper;

    public SkippingResourcesAsToolsPresenter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public ToolSpecification createListResourcesSpecification() {
        return defaults.createListResourcesSpecification();
    }

    @Override
    public ToolExecutor createListResourcesExecutor(List<McpClient> mcpClients) {
        return new ToolExecutor() {

            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
                var descriptions = new ArrayList<ResourceDescription>();
                for (var client : mcpClients) {
                    listOrSkip(client, () -> client.listResources(context)).forEach(resource -> descriptions.add(new ResourceDescription(
                            client.key(), resource.uri(), null, resource.name(), resource.description(), resource.mimeType())));
                    listOrSkip(client, () -> client.listResourceTemplates(context)).forEach(template -> descriptions.add(new ResourceDescription(
                            client.key(), null, template.uriTemplate(), template.name(), template.description(), template.mimeType())));
                }
                return ToolExecutionResult.builder().resultText(jsonMapper.writeValueAsString(descriptions)).build();
            }

            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return executeWithContext(request, null).resultText();
            }
        };
    }

    @Override
    public ToolSpecification createGetResourceSpecification() {
        return defaults.createGetResourceSpecification();
    }

    @Override
    public ToolExecutor createGetResourceExecutor(List<McpClient> mcpClients) {
        return defaults.createGetResourceExecutor(mcpClients);
    }

    private static <T> List<T> listOrSkip(McpClient client, Supplier<List<T>> listCall) {
        try {
            return listCall.get();
        } catch (McpException e) {
            if (e.errorCode() == METHOD_NOT_FOUND) {
                log.debug("MCP server {} serves no resources: {}", client.key(), e.errorMessage());
                return List.of();
            }
            throw new ToolExecutionException(e);
        }
    }

    record ResourceDescription(String mcpServer, String uri, String uriTemplate, String name, String description, String mimeType) {
    }
}
