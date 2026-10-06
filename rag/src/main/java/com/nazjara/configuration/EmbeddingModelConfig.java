package com.nazjara.configuration;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The embedding model shared by ingestion and retrieval, under every profile.
 *
 * <p>{@link AllMiniLmL6V2EmbeddingModel} runs all-MiniLM-L6-v2 in-process through ONNX Runtime and
 * returns 384-dimensional vectors. The model and its tokenizer ship inside the
 * {@code langchain4j-embeddings-all-minilm-l6-v2} jar, so nothing is downloaded at startup. Spring
 * AI's {@code spring-ai-starter-model-transformers} runs the same model but downloads it on first
 * use. The two produce the same vectors, so retrieval results are comparable across the repos.
 *
 * <p>The starter does not create an {@link EmbeddingModel}; once this bean exists, its
 * {@code RagAutoConfiguration} combines it with the {@code EmbeddingStore} bean into an
 * {@code EmbeddingStoreContentRetriever} (see {@code RagAssistant}).
 */
@Configuration
public class EmbeddingModelConfig {

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
