package com.nazjara.bootstrap;

import com.nazjara.configuration.EmbeddingStoreProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import java.io.IOException;
import java.io.UncheckedIOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Loads every configured document into an {@link EmbeddingStore}. Used by both the in-memory store
 * ({@code EmbeddingStoreConfig}) and Milvus ({@code MilvusEmbeddingStoreLoader}).
 *
 * <p>The pipeline is the same read → split → embed → store sequence as Spring AI's
 * {@code TikaDocumentReader} → {@code TokenTextSplitter} → {@code vectorStore.add(...)}, but in
 * LangChain4j each step is a separate object:
 * <ol>
 *   <li>{@link ApacheTikaDocumentParser} turns the raw bytes (txt, HTML, PDF, ...) into one
 *       {@link Document}: the whole text plus {@code Metadata}.</li>
 *   <li>{@link EmbeddingStoreIngestor} runs the rest: the {@code DocumentSplitter} cuts the
 *       document into {@link TextSegment}s (each copies the document's metadata and adds an
 *       {@code index}), the {@link EmbeddingModel} embeds them in one batch, and
 *       {@code EmbeddingStore.addAll} stores vector + segment pairs.</li>
 * </ol>
 * {@code DocumentSplitters.recursive} splits by paragraph, then line, sentence and word until each
 * piece fits. Sizes are measured with {@link HuggingFaceTokenCountEstimator}, which uses the same
 * BERT tokenizer as MiniLM, so a 256-token segment fits the model's input window.
 * {@code TokenTextSplitter}'s default is 800 tokens, which MiniLM would truncate.
 *
 * <p>LangChain4j also offers {@code UrlDocumentLoader} / {@code ClassPathDocumentLoader} /
 * {@code FileSystemDocumentLoader}; this class reads Spring {@link Resource}s instead so one
 * property list can mix {@code classpath:} and {@code https:} entries, as in Spring AI.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DocumentIngester {

    private static final int MAX_SEGMENT_TOKENS = 256;
    private static final int OVERLAP_TOKENS = 32;
    private static final String SOURCE_METADATA_KEY = "source";

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreProperties embeddingStoreProperties;

    /**
     * Parses, splits, embeds and stores every document listed in {@code ai.rag.documents-to-load}.
     *
     * @param embeddingStore the store to write the segments into
     * @throws UncheckedIOException if a document cannot be read
     */
    public void ingest(EmbeddingStore<TextSegment> embeddingStore) {
        var parser = new ApacheTikaDocumentParser();
        var ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(MAX_SEGMENT_TOKENS, OVERLAP_TOKENS,
                        new HuggingFaceTokenCountEstimator()))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        embeddingStoreProperties.getDocumentsToLoad().forEach(resource -> {
            log.debug("Loading document: {}", resource.getDescription());
            var document = parse(parser, resource);
            document.metadata().put(SOURCE_METADATA_KEY, resource.getDescription());
            ingestor.ingest(document);
        });
    }

    private Document parse(ApacheTikaDocumentParser parser, Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return parser.parse(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource.getDescription(), e);
        }
    }
}
