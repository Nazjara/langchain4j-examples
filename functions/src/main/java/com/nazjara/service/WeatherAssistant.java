package com.nazjara.service;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * Weather assistant backed by the {@code currentWeather} tool.
 *
 * <p>The starter wires every {@code @Tool} bean into this AI Service, so each request lists the
 * tool's name, description and JSON schema. The tool loop lives inside the generated proxy, not in
 * an advisor:
 * <ol>
 *   <li>The proxy sends system message, user message and tool specifications to the
 *       {@code ChatModel}.</li>
 *   <li>If the reply holds {@code ToolExecutionRequest}s instead of (or besides) text, the proxy's
 *       {@code ToolService} runs each one, appends the {@code AiMessage} and one
 *       {@code ToolExecutionResultMessage} per call to the conversation, and calls the model
 *       again.</li>
 *   <li>It repeats until the reply has no tool requests, then maps that final reply to the return
 *       type.</li>
 * </ol>
 * The loop is capped by {@code maxToolCallingRoundTrips} (default 100) and errors are routed
 * through {@code ToolExecutionErrorHandler} / {@code ToolArgumentsErrorHandler}. The starter does
 * not expose either setting; both need {@code AiServices.builder(...)}, shown in
 * {@code ProgrammaticToolsTest}.
 *
 * <p>In Spring AI the same loop is the {@code ToolCallingAdvisor} that {@code ChatClient}
 * auto-registers, and tools are passed per call with {@code .tools(...)}.
 */
@AiService
public interface WeatherAssistant {

    /**
     * Answers a weather question, calling the weather tool as many times as the model needs.
     *
     * @param question the user's question
     * @return the model's final answer
     */
    @SystemMessage(fromResource = "/prompts/weather-system.txt")
    String ask(String question);

    /**
     * Same as {@link #ask(String)}, but wraps the answer in {@link Result}, whose
     * {@code toolExecutions()} lists every tool call the loop made (request, result, failure flag).
     * Spring AI has no direct equivalent on {@code ChatClient}: the tool calls happen inside the
     * advisor and are not returned with the response.
     *
     * @param question the user's question
     * @return the answer plus the tool executions that produced it
     */
    @SystemMessage(fromResource = "/prompts/weather-system.txt")
    Result<String> askWithToolCalls(String question);
}
