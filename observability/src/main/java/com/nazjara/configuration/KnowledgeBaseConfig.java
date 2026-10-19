package com.nazjara.configuration;

import com.nazjara.observation.RetrievalObservationListener;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The café knowledge base: {@code knowledge/cafe.md} in an {@link InMemoryEmbeddingStore},
 * embedded by the local all-MiniLM-L6-v2 model, behind a {@link ContentRetriever} that reports to
 * {@link RetrievalObservationListener}.
 *
 * <p>The retriever is declared here instead of taken from the starter: the starter's
 * {@code RagAutoConfiguration} would build the same {@code EmbeddingStoreContentRetriever} from
 * these two beans, but offers no way to add a listener. Its own retriever is
 * {@code @ConditionalOnMissingBean}, so this one replaces it. {@code addListener} wraps it in a
 * {@code ListeningContentRetriever}, which calls the listener around every {@code retrieve}.
 *
 * <p>The knowledge base is loaded, split and embedded at startup. {@link ClassPathDocumentLoader}
 * adds the {@code file_name} metadata and the splitter adds {@code index}; the retrieval span uses
 * both to say which chunk came back. Small segments keep each chunk to about one section of the
 * file.
 */
@Configuration
public class KnowledgeBaseConfig {

    private static final String KNOWLEDGE_BASE = "knowledge/cafe.md";
    private static final int MAX_SEGMENT_TOKENS = 80;

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel) {
        var embeddingStore = new InMemoryEmbeddingStore<TextSegment>();
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(MAX_SEGMENT_TOKENS, 0, new HuggingFaceTokenCountEstimator()))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(ClassPathDocumentLoader.loadDocument(KNOWLEDGE_BASE, new TextDocumentParser()));
        return embeddingStore;
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel,
                                      ObservabilityProperties properties, RetrievalObservationListener listener) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(properties.retrievalMaxResults())
                .build()
                .addListener(listener);
    }
}
