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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Direct calls on the {@link McpClient}, one method per MCP request type.
 *
 * <p>MCP servers offer three kinds of things. Tools are model-controlled and reach the model
 * through {@code McpToolProvider}. Resources and prompts are application-controlled: no AI Service
 * slot uses them, so the application calls the client itself, as this class does.
 *
 * <p><b>LangChain4j's client supports all three; LangChain4j's server supports tools only.</b>
 * Against the {@code mcp-server} module, every resource and prompt method below fails with an
 * {@link McpException} carrying JSON-RPC error {@code -32601} ("Method not found"), because
 * {@code langchain4j-community-mcp-server} has no way to register a resource or a prompt. The code
 * is correct and works against any server that does serve them ({@code McpServerGatewayTest} proves
 * it with a stubbed client), but the two halves of the framework cannot demonstrate it together.
 * Spring AI's server publishes all three ({@code @McpTool}, {@code @McpResource},
 * {@code @McpPrompt}).
 */
@Service
@RequiredArgsConstructor
public class McpServerGateway {

    private final McpClient mcpClient;

    /**
     * Lists the server's tools ({@code tools/list}). Works against the {@code mcp-server} module.
     * The metadata map carries what the server sent besides name, description and schema: the title
     * and the {@code readOnlyHint}/{@code destructiveHint}/{@code idempotentHint} annotations.
     *
     * @return name, description and metadata of each tool
     * @throws McpException if the server answers with a JSON-RPC error
     */
    public List<ToolInfo> tools() {
        return mcpClient.listTools().stream()
                .map(tool -> new ToolInfo(tool.name(), tool.description(), tool.metadata()))
                .toList();
    }

    /**
     * Lists the server's fixed resources ({@code resources/list}) and URI templates
     * ({@code resources/templates/list}).
     *
     * <p><b>Not served by the {@code mcp-server} module:</b> LangChain4j's server has no resource
     * support, so this call fails with {@code -32601}.
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
     * <p><b>Not served by the {@code mcp-server} module:</b> LangChain4j's server has no resource
     * support, so this call fails with {@code -32601}.
     *
     * @param uri resource URI, e.g. {@code movies://Avatar}
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
     * Lists the server's prompt templates ({@code prompts/list}).
     *
     * <p><b>Not served by the {@code mcp-server} module:</b> LangChain4j's server has no prompt
     * support, so this call fails with {@code -32601}.
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
     * Asks the server to render a prompt ({@code prompts/get}) and converts the result into
     * LangChain4j chat messages. The server fills the arguments into its template and returns
     * ready-to-send messages with roles; {@link McpPromptMessage#toChatMessage()} maps a
     * {@code user} message to {@code UserMessage} and an {@code assistant} message to
     * {@code AiMessage}. Spring AI has no such conversion; its MCP prompt messages are mapped by
     * hand.
     *
     * <p><b>Not served by the {@code mcp-server} module:</b> LangChain4j's server has no prompt
     * support, so this call fails with {@code -32601}.
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
