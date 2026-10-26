package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.PromptInfo;
import com.nazjara.model.Question;
import com.nazjara.model.ResourceCatalog;
import com.nazjara.model.ResourceQuestion;
import com.nazjara.model.ToolInfo;
import com.nazjara.service.McpAssistant;
import com.nazjara.service.McpServerGateway;
import dev.langchain4j.mcp.client.McpException;
import dev.langchain4j.model.chat.ChatModel;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for LangChain4j's MCP client: one tool-calling endpoint through an AI Service,
 * and one endpoint per direct client call.
 *
 * <p>{@code POST /ask} and {@code GET /tools} use both servers. The resource and prompt endpoints
 * use the {@code mcp-http-server} module, the one that serves them (see {@link McpServerGateway}).
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private static final int METHOD_NOT_FOUND = -32601;
    private static final int INVALID_PARAMS = -32602;
    private static final int RESOURCE_NOT_FOUND = -32002;

    private final McpAssistant mcpAssistant;
    private final McpServerGateway mcpServerGateway;
    private final ChatModel chatModel;

    /**
     * Free-form question; Claude calls the MCP tools of either server when it needs them, and may
     * list and read the HTTP server's resources through the resources-as-tools pair.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's answer
     */
    @PostMapping("/ask")
    public Answer askQuestion(@RequestBody Question question) {
        return new Answer(mcpAssistant.ask(question.question()));
    }

    /**
     * Lists the tools both MCP servers publish.
     *
     * @return the tools, each with its server key
     */
    @GetMapping("/tools")
    public List<ToolInfo> listTools() {
        return mcpServerGateway.tools();
    }

    /**
     * Lists the resources and resource templates the HTTP MCP server publishes.
     *
     * @return the server's resources
     */
    @GetMapping("/resources")
    public ResourceCatalog listResources() {
        return mcpServerGateway.resources();
    }

    /**
     * Reads an MCP resource from the HTTP server and answers a question from its content.
     *
     * @param resourceQuestion JSON body {@code {"uri": "...", "question": "..."}}
     * @return the model's answer
     */
    @PostMapping("/resources/ask")
    public Answer askAboutResource(@RequestBody ResourceQuestion resourceQuestion) {
        var resource = mcpServerGateway.readResource(resourceQuestion.uri());
        return new Answer(mcpAssistant.askAboutResource(resource, resourceQuestion.question()));
    }

    /**
     * Lists the prompt templates the HTTP MCP server publishes.
     *
     * @return the server's prompts
     */
    @GetMapping("/prompts")
    public List<PromptInfo> listPrompts() {
        return mcpServerGateway.prompts();
    }

    /**
     * Renders a server-side prompt and sends the resulting messages to Claude.
     *
     * <p>The messages go to the {@link ChatModel} directly: an AI Service method takes one user
     * message, while an MCP prompt can be a list of user and assistant turns. This path therefore
     * has no tool loop.
     *
     * @param name prompt name
     * @param arguments JSON object with the prompt's arguments
     * @return the model's answer
     */
    @PostMapping("/prompts/{name}")
    public Answer runPrompt(@PathVariable String name, @RequestBody Map<String, Object> arguments) {
        var messages = mcpServerGateway.renderPrompt(name, arguments);
        return new Answer(chatModel.chat(messages).aiMessage().text());
    }

    /**
     * Turns a JSON-RPC error from the MCP server into an HTTP error. "Method not found" means the
     * server does not implement that part of MCP (501). "Invalid params", e.g. a bad prompt
     * argument, and "Resource not found" are the caller's mistakes (400, 404). Any other error is
     * the server's (502).
     *
     * @param exception the error returned by the MCP server
     * @return problem details with the server's error code and message
     */
    @ExceptionHandler(McpException.class)
    public ProblemDetail handleMcpError(McpException exception) {
        var status = switch (exception.errorCode()) {
            case METHOD_NOT_FOUND -> HttpStatus.NOT_IMPLEMENTED;
            case INVALID_PARAMS -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, exception.errorMessage());
        problem.setTitle("MCP server error " + exception.errorCode());
        return problem;
    }
}
