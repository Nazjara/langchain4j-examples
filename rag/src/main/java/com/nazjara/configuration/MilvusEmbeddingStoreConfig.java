package com.nazjara.configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.v2.MilvusV2EmbeddingStore;
import io.milvus.v2.common.IndexParam.IndexType;
import io.milvus.v2.common.IndexParam.MetricType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * {@code prod} embedding store: Milvus, started with this module's {@code docker-compose.yml}.
 *
 * <p>Spring AI auto-configures its Milvus {@code VectorStore} from
 * {@code spring.ai.vectorstore.milvus.*}. LangChain4j has no Spring Boot 4 starter for Milvus, so
 * the store is built by hand from {@code ai.rag.milvus.*}. {@link MilvusV2EmbeddingStore} (artifact
 * {@code langchain4j-milvus-v2}) uses Milvus' v2 client; the older {@code MilvusEmbeddingStore} in
 * {@code langchain4j-milvus} is deprecated.
 *
 * <p>The builder creates the collection on first use with the given dimension (384 for MiniLM),
 * index and metric. The collection name differs from Spring AI's so the two repos can share one
 * Milvus instance: each library uses its own field layout.
 */
@Configuration
@Profile("prod")
public class MilvusEmbeddingStoreConfig {

    private static final int MINILM_DIMENSION = 384;

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(EmbeddingStoreProperties embeddingStoreProperties) {
        var milvus = embeddingStoreProperties.getMilvus();
        return MilvusV2EmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getPort())
                .username(milvus.getUsername())
                .password(milvus.getPassword())
                .databaseName(milvus.getDatabaseName())
                .collectionName(milvus.getCollectionName())
                .dimension(MINILM_DIMENSION)
                .indexType(IndexType.IVF_FLAT)
                .metricType(MetricType.COSINE)
                .build();
    }
}
