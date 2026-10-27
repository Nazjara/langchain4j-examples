package com.nazjara.service;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Streaming twin of {@link TravelAssistant}. Returning {@link TokenStream} tells the AI Service
 * proxy to call the {@code StreamingChatModel} and hand back a builder of callbacks instead of a
 * result: nothing is sent to the model until {@code start()}, and the tool loop (model call, tool
 * execution, next model call) runs inside the stream, reporting each step to the callbacks.
 *
 * <p>Spring AI's counterpart is {@code ChatClient.stream()}, which returns a {@code Flux} of
 * response chunks.
 */
public interface StreamingTravelAssistant {

    /**
     * Starts answering a travel question.
     *
     * @param question the user's question
     * @param parameters per-request context for the tools and the tool provider (user id, role),
     *                   never shown to the model
     * @return the callback builder; call {@code start()} after registering handlers
     */
    @SystemMessage(fromResource = "/prompts/travel-system.txt")
    TokenStream ask(@UserMessage String question, InvocationParameters parameters);
}
