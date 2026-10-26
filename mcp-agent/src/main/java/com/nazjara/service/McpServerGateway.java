package com.nazjara.service;

import com.nazjara.model.PromptInfo;
import com.nazjara.model.ResourceCatalog;
import com.nazjara.model.ResourceInfo;
import com.nazjara.model.ToolInfo;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.mcp.client.McpPromptArgument;
import dev.langchain4j.mcp.client.McpPromptMessage;
import dev.langchain4j.mcp.client.McpTextResourceContents;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Direct calls on the MCP clients, one method per MCP request type.
 *
 * <p>MCP servers offer three kinds of things. Tools are model-controlled and reach the model
 * through {@code McpToolProvider}. Resources are application-controlled and prompts are
 * user-controlled: no AI Service slot uses them, so the application calls a client itself, as this
 * class does.
 *
 * <p>Tools are listed from every server. Resources and prompts are read from the
 * {@code mcp-http-server} module only, because LangChain4j's own server ({@code mcp-server}) has
 * no way to register either and answers every such request with JSON-RPC error {@code -32601}
 * ("Method not found"). The HTTP server is built on the MCP Java SDK, so it is the only place in
 * this repository where LangChain4j's resource and prompt client calls meet a real server.
 */
@Service
public class McpServerGateway {

    private final List<McpClient> mcpClients;
    private final McpClient mcpClient;

    /**
     * Creates the gateway.
     *
     * @param mcpClients clients of all servers, whose tools are listed
     * @param contentMcpClient client of the server that serves resources and prompts
     */
    public McpServerGateway(List<McpClient> mcpClients, @Qualifier("openMeteoMcpClient") McpClient contentMcpClient) {
        this.mcpClients = mcpClients;
        this.mcpClient = contentMcpClient;
    }

    /**
     * Lists every server's tools ({@code tools/list}), each with the key of the client it came
     * from. The metadata map carries what the server sent besides name, description and schema:
     * the title and the {@code readOnlyHint}/{@code destructiveHint}/{@code idempotentHint}/
     * {@code openWorldHint} annotations.
     *
     * @return server key, name, description and metadata of each tool
     * @throws McpException if a server answers with a JSON-RPC error
     */
    public List<ToolInfo> tools() {
        return mcpClients.stream()
                .flatMap(client -> client.listTools().stream()
                        .map(tool -> new ToolInfo(client.key(), tool.name(), tool.description(), tool.metadata())))
                .toList();
    }

    /**
     * Lists the HTTP server's fixed resources ({@code resources/list}) and URI templates
     * ({@code resources/templates/list}).
     *
     * @return the resources and the resource templates
     * @throws McpException if the server answers with a JSON-RPC error
     */
    public ResourceCatalog resources() {
        var resources = mcpClient.listResources().stream()
                .map(resource -> new ResourceInfo(resource.uri(), resource.name(), resource.description(), resource.mimeType()))
                .toList();
        var templates = mcpClient.listResourceTemplates().stream()
                .map(template -> new ResourceInfo(template.uriTemplate(), template.name(), template.description(), template.mimeType()))
                .toList();
        return new ResourceCatalog(resources, templates);
    }

    /**
     * Reads one resource by URI ({@code resources/read}) and joins its text parts. A resource may
     * also hold binary parts ({@code McpBlobResourceContents}); those are skipped.
     *
     * @param uri resource URI, e.g. {@code weather://wmo-codes} or {@code weather://city/Lviv}
     * @return the resource's text content
     * @throws McpException if the server answers with a JSON-RPC error
     */
    public String readResource(String uri) {
        return mcpClient.readResource(uri).contents().stream()
                .filter(McpTextResourceContents.class::isInstance)
                .map(McpTextResourceContents.class::cast)
                .map(McpTextResourceContents::text)
                .collect(Collectors.joining("\n"));
    }

    /**
     * Lists the HTTP server's prompt templates ({@code prompts/list}).
     *
     * @return name, description and argument names of each prompt
     * @throws McpException if the server answers with a JSON-RPC error
     */
    public List<PromptInfo> prompts() {
        return mcpClient.listPrompts().stream()
                .map(prompt -> new PromptInfo(prompt.name(), prompt.description(),
                        prompt.arguments().stream().map(McpPromptArgument::name).toList()))
                .toList();
    }

    /**
     * Asks the HTTP server to render a prompt ({@code prompts/get}) and converts the result into
     * LangChain4j chat messages. The server fills the arguments into its template and returns
     * ready-to-send messages with roles; {@link McpPromptMessage#toChatMessage()} maps a
     * {@code user} message to {@code UserMessage} and an {@code assistant} message to
     * {@code AiMessage}; an embedded text resource becomes the text of a {@code UserMessage}. Spring AI has no such conversion; its MCP prompt messages are mapped by
     * hand.
     *
     * @param name prompt name
     * @param arguments values for the prompt's arguments
     * @return the rendered prompt as chat messages
     * @throws McpException if the server answers with a JSON-RPC error
     */
    public List<ChatMessage> renderPrompt(String name, Map<String, Object> arguments) {
        return mcpClient.getPrompt(name, arguments).messages().stream()
                .map(McpPromptMessage::toChatMessage)
                .toList();
    }
}
