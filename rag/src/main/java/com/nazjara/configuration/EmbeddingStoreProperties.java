package com.nazjara.configuration;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * RAG settings bound from {@code ai.rag.*}: the documents (classpath or URL) to ingest and the
 * Milvus connection used under the {@code prod} profile. Spring AI's {@code VectorStoreProperties}
 * also has a file path for saving the in-memory store; this module doesn't persist it (see
 * {@code EmbeddingStoreConfig}). Spring AI gets its Milvus settings from the starter's
 * {@code spring.ai.vectorstore.milvus.*} properties.
 */
@Configuration
@ConfigurationProperties(prefix = "ai.rag")
@Getter
@Setter
public class EmbeddingStoreProperties {

    private List<Resource> documentsToLoad;
    private Milvus milvus = new Milvus();

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
