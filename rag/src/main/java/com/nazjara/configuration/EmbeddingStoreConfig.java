package com.nazjara.configuration;

import com.nazjara.bootstrap.DocumentIngester;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Default (non-{@code prod}) embedding store: an {@link InMemoryEmbeddingStore}, the counterpart of
 * Spring AI's {@code SimpleVectorStore}. Vectors and segments live only on the heap, and every
 * search is a brute-force scan over them. They are lost on shutdown, so every startup re-ingests
 * all documents.
 *
 * <p>Unlike Spring AI's {@code VectorStore}, a LangChain4j {@link EmbeddingStore} never embeds
 * anything: it only stores and searches vectors. The {@code EmbeddingModel} is passed separately to
 * whoever writes (the ingester) and whoever searches (the content retriever).
 *
 * <p>Declaring this bean also switches off the starter's own fallback, an empty
 * {@code InMemoryEmbeddingStore} registered with {@code @ConditionalOnMissingBean}.
 *
 * <p>Under the {@code prod} profile this config is inactive and Milvus, which persists on its own,
 * is used instead (see {@code MilvusEmbeddingStoreConfig}).
 */
@Configuration
@Profile("!prod")
public class EmbeddingStoreConfig {

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(DocumentIngester documentIngester) {
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        documentIngester.ingest(embeddingStore);
        return embeddingStore;
    }
}
