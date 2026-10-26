package com.nazjara.service;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * RAG as an AI Service slot: the interface has no retrieval code at all. Every question searches
 * every source (trucks and boats).
 *
 * <p>Before each model call, the proxy hands the user message to the {@code searchAllAugmentor}
 * bean ({@code RetrievalAugmentorConfig}), which searches each source for its own top chunks and
 * appends them to the user message. {@link Result#sources()} returns the chunks that were injected.
 *
 * <p>The module has two {@code RetrievalAugmentor} beans, so automatic wiring would fail on the
 * duplicate; {@code EXPLICIT} wiring names the beans to use. {@code anthropicChatModel} is the bean
 * the Anthropic starter registers.
 *
 * <p>Spring AI's equivalent is {@code QuestionAnswerAdvisor} (or the modular
 * {@code RetrievalAugmentationAdvisor}) added to the {@code ChatClient}.
 */
@AiService(wiringMode = EXPLICIT, chatModel = "anthropicChatModel", retrievalAugmentor = "searchAllAugmentor")
public interface RagAssistant {

    /**
     * Answers a question, with retrieval from every source done by the proxy before the model call.
     *
     * @param question the user's question
     * @return the model's answer plus the injected chunks ({@code sources()})
     */
    @SystemMessage(fromResource = "/prompts/system-message.txt")
    Result<String> ask(String question);
}
