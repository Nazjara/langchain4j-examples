package com.nazjara.service;

import static java.util.stream.Collectors.joining;

import com.nazjara.model.Answer;
import com.nazjara.model.Question;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * RAG implemented by hand, so each step is visible. Port of Spring AI's {@code AiServiceImpl}
 * (renamed so it does not clash with LangChain4j's {@code @AiService} annotation):
 * <ol>
 *   <li>The {@link EmbeddingModel} embeds the question; Spring AI's
 *       {@code VectorStore.similaritySearch} does this internally.</li>
 *   <li>{@link EmbeddingStore#search} returns the 4 closest {@link TextSegment}s.</li>
 *   <li>The segments and the question fill {@code prompts/rag-prompt-template.txt}
 *       ({@link PromptTemplate}, {@code {{var}}} syntax), which becomes the user message.</li>
 *   <li>{@link ChatModel#chat} sends the system message ({@code prompts/system-message.txt}) and
 *       that user message.</li>
 * </ol>
 * No AI Service is involved: this uses the low-level {@code ChatModel} the Anthropic starter
 * registers. {@link RagAssistant} does the same steps declaratively.
 */
@Service
public class ManualRagService {

    private static final int MAX_RESULTS = 4;

    private final ChatModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final SystemMessage systemMessage;
    private final PromptTemplate ragPromptTemplate;

    public ManualRagService(ChatModel chatModel, EmbeddingStore<TextSegment> embeddingStore,
                            EmbeddingModel embeddingModel,
                            @Value("classpath:/prompts/system-message.txt") Resource systemMessageTemplate,
                            @Value("classpath:/prompts/rag-prompt-template.txt") Resource ragPromptTemplate)
            throws IOException {
        this.chatModel = chatModel;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.systemMessage = SystemMessage.from(systemMessageTemplate.getContentAsString(StandardCharsets.UTF_8));
        this.ragPromptTemplate = PromptTemplate.from(ragPromptTemplate.getContentAsString(StandardCharsets.UTF_8));
    }

    /**
     * Answers a question from the 4 most similar document segments.
     *
     * @param question the user's question
     * @return the model's answer
     */
    public Answer getAnswer(Question question) {
        var searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(question.question()).content())
                .maxResults(MAX_RESULTS)
                .build();
        var documents = embeddingStore.search(searchRequest).matches().stream()
                .map(match -> match.embedded().text())
                .collect(joining("\n"));

        var userMessage = ragPromptTemplate
                .apply(Map.of("input", question.question(), "documents", documents))
                .toUserMessage();

        var response = chatModel.chat(systemMessage, userMessage);
        return new Answer(response.aiMessage().text());
    }
}
