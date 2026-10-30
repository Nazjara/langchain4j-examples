package com.nazjara.service;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Café assistant with RAG over {@code knowledge/cafe.md} and the {@code CafeTools} tool.
 * {@code AiServicesConfig} builds it with {@code AiServices.builder(...)}, because
 * {@code @AiService} has no attribute for AI Service listeners or tool execution hooks.
 *
 * <p>One call runs the whole pipeline inside the proxy, on the caller's thread: retrieval
 * (embed the question, search the store, append the chunks to the user message), the first
 * model call, the tool if the model asks for it, and a second model call. That single thread is
 * what lets every span of the call nest under the HTTP request span.
 *
 * <p>{@code @UserMessage("{{it}}")} puts the question into the message as a value; without it the
 * question would itself be rendered as a template (see {@code basics}' {@code Assistant}).
 */
public interface CafeAssistant {

    /**
     * Answers a guest question from the knowledge base and the tool.
     *
     * @param question the guest's question
     * @return the answer plus {@code sources()} (the retrieved chunks), tool executions and token
     * usage summed over all model calls
     */
    @SystemMessage(fromResource = "/prompts/cafe-system.txt")
    @UserMessage("{{it}}")
    Result<String> ask(String question);
}
