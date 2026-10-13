package com.nazjara.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * AI Service whose tools come from an MCP server instead of local {@code @Tool} beans.
 *
 * <p>The interface has no MCP code. The starter finds the {@code ToolProvider} bean
 * ({@code McpToolProvider}) and sets it on the proxy. On each call the proxy asks the provider for
 * tools, sends their specifications to Claude, and when Claude replies with a tool request it runs
 * the matching {@code McpToolExecutor}, which sends {@code tools/call} to the server and returns
 * the text of the result. Claude only ever sees a tool named {@code currentWeather}; it cannot tell
 * the tool is remote.
 *
 * <p>In Spring AI the MCP tools arrive as a {@code ToolCallbackProvider} that is passed to
 * {@code ChatClient.Builder.defaultTools(...)}.
 */
@AiService
public interface McpAssistant {

    /**
     * Answers a question, calling the server's MCP tools as often as the model needs.
     *
     * @param question the user's question
     * @return the model's final answer
     */
    @SystemMessage(fromResource = "/prompts/weather-system.txt")
    String ask(String question);

    /**
     * Answers a question from the text of an MCP resource the application has already read.
     * Resources are application-controlled: the model does not fetch them, the caller puts their
     * content into the prompt.
     *
     * @param resource text content of the resource
     * @param question the user's question
     * @return the model's answer
     */
    @SystemMessage(fromResource = "/prompts/resource-system.txt")
    String askAboutResource(@V("resource") String resource, @UserMessage String question);
}
