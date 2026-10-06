package com.nazjara.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * RAG as an AI Service slot: the interface has no retrieval code at all.
 *
 * <p>The starter's {@code RagAutoConfiguration} sees the {@code EmbeddingModel} and
 * {@code EmbeddingStore} beans and registers an {@code EmbeddingStoreContentRetriever}
 * ({@code langchain4j.rag.retrieval.max-results=4}). {@code @AiService} auto-wiring then puts that
 * {@code ContentRetriever} into the proxy, which wraps it in a {@code DefaultRetrievalAugmentor}
 * and runs its stages before each model call:
 * <ol>
 *   <li>{@code QueryTransformer}: the user message becomes a {@code Query} (default: unchanged).</li>
 *   <li>{@code QueryRouter}: picks the retrievers for the query (default: all of them).</li>
 *   <li>{@code ContentRetriever}: embeds the query and searches the store.</li>
 *   <li>{@code ContentAggregator}: merges and ranks results (default: reciprocal-rank fusion).</li>
 *   <li>{@code ContentInjector}: appends the segments to the <em>user</em> message under
 *       "Answer using the following information:".</li>
 * </ol>
 * The system message is the same as {@link ManualRagService}'s, but the RAG template is
 * LangChain4j's built-in one, not {@code rag-prompt-template.txt}.
 *
 * <p>Spring AI's equivalent is {@code QuestionAnswerAdvisor} (or the modular
 * {@code RetrievalAugmentationAdvisor}) added to the {@code ChatClient}.
 */
@AiService
public interface RagAssistant {

    /**
     * Answers a question, with retrieval done by the proxy before the model call.
     *
     * @param question the user's question
     * @return the model's answer
     */
    @SystemMessage(fromResource = "/prompts/system-message.txt")
    String ask(String question);
}
