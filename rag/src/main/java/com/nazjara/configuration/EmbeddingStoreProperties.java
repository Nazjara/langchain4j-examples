package com.nazjara.configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * RAG settings bound from {@code ai.rag.*}: the knowledge sources, how many chunks each source
 * returns, and the Milvus connection used under the {@code prod} profile.
 *
 * <p>Each entry of {@code ai.rag.sources} is one source: its key (e.g. {@code trucks}) becomes the
 * {@code category} tag of every chunk of its documents, and its description is what Claude reads
 * when {@code LanguageModelQueryRouter} picks sources (see {@code RetrievalAugmentorConfig}).
 * Spring AI's {@code VectorStoreProperties} has a flat document list and a file path for saving the
 * in-memory store; this module doesn't persist it (see {@code EmbeddingStoreConfig}). Spring AI gets
 * its Milvus settings from the starter's {@code spring.ai.vectorstore.milvus.*} properties.
 */
@Configuration
@ConfigurationProperties(prefix = "ai.rag")
@Getter
@Setter
public class EmbeddingStoreProperties {

    private Map<String, Source> sources = new LinkedHashMap<>();
    private int maxResultsPerSource = 3;
    private Milvus milvus = new Milvus();

    @Getter
    @Setter
    public static class Source {

        private String description;
        private List<Resource> documents = List.of();
    }

    @Getter
    @Setter
    public static class Milvus {

        private String host;
        private Integer port;
        private String username;
        private String password;
        private String databaseName;
        private String collectionName;
    }
}
