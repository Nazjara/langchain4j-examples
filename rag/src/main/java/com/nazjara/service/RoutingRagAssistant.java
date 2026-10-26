package com.nazjara.service;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * Same as {@link RagAssistant}, but Claude first picks which sources to search.
 *
 * <p>The {@code routingAugmentor} bean ({@code RetrievalAugmentorConfig}) sends Claude a short
 * prompt listing the sources' descriptions and searches only the ones it names. "How much can the
 * Chevy Colorado tow?" searches trucks only; "What is a good truck to pull a Sportsman 232 boat?"
 * needs both. {@link Result#sources()} shows which sources were searched: every chunk carries its
 * {@code category}. The routing call is a separate, short Claude request.
 */
@AiService(wiringMode = EXPLICIT, chatModel = "anthropicChatModel", retrievalAugmentor = "routingAugmentor")
public interface RoutingRagAssistant {

    /**
     * Answers a question from the sources Claude picked for it.
     *
     * @param question the user's question
     * @return the model's answer plus the injected chunks ({@code sources()})
     */
    @SystemMessage(fromResource = "/prompts/system-message.txt")
    Result<String> ask(String question);
}
