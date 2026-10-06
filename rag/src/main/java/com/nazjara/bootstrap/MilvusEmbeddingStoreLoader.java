package com.nazjara.bootstrap;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ingests the configured documents into Milvus on startup ({@code prod} profile only).
 *
 * <p>Same idea as Spring AI's {@code MilvusVectorStoreLoader}: a probe search decides whether the
 * collection is already filled. Spring AI's {@code vectorStore.similaritySearch("Sportsman")}
 * embeds the text internally; here the probe is embedded explicitly, because an
 * {@code EmbeddingStore} only accepts vectors.
 */
@Profile("prod")
@Component
@Slf4j
@RequiredArgsConstructor
public class MilvusEmbeddingStoreLoader implements CommandLineRunner {

    private static final String PROBE_QUERY = "Sportsman";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final DocumentIngester documentIngester;

    /**
     * Ingests all documents unless the probe search already finds a segment.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    public void run(String... args) {
        log.info("Loading Milvus embedding store...");

        var probe = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(PROBE_QUERY).content())
                .maxResults(1)
                .build();

        if (embeddingStore.search(probe).matches().isEmpty()) {
            log.info("Loading documents into embedding store...");
            documentIngester.ingest(embeddingStore);
        }

        log.info("Loading done");
    }
}
