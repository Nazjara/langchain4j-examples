package com.nazjara.service;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Travel assistant over the 15 {@code TravelTools}. {@code AiServicesConfig} builds this interface
 * several times (one per tool search mode, the role-aware one and the skills one), because
 * {@code @AiService} has no attribute for a {@code ToolSearchStrategy}, a system message
 * transformer or {@code maxToolCallingRoundTrips}.
 *
 * <p>The {@link InvocationParameters} argument is not part of the prompt. The proxy finds it by
 * type, stores it in the invocation's {@code InvocationContext}, and injects it into every tool
 * method that declares an {@code InvocationParameters} parameter. Tool search strategies and
 * guardrails receive the same context. Spring AI's counterpart is
 * {@code .toolContext(Map.of(...))} on the {@code ChatClient} request, read through a
 * {@code ToolContext} tool parameter.
 */
public interface TravelAssistant {

    /**
     * Answers a travel question, calling as many tools as the model needs.
     *
     * @param question the user's question
     * @param parameters per-request context for the tools (the user id), never shown to the model
     * @return the answer plus token usage, tool executions and intermediate responses
     */
    @SystemMessage(fromResource = "/prompts/travel-system.txt")
    Result<String> ask(@UserMessage String question, InvocationParameters parameters);
}
