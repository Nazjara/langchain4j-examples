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
 * Loads the documents of every configured source into an {@link EmbeddingStore}. Used by both the
 * in-memory store ({@code EmbeddingStoreConfig}) and Milvus ({@code MilvusEmbeddingStoreLoader}).
 *
 * <p>The pipeline is the same read → split → embed → store sequence as Spring AI's
 * {@code TikaDocumentReader} → {@code TokenTextSplitter} → {@code vectorStore.add(...)}, but in
 * LangChain4j each step is a separate object:
 * <ol>
 *   <li>{@link ApacheTikaDocumentParser} turns the raw bytes (txt, HTML, PDF, ...) into one
 *       {@link Document}: the whole text plus {@code Metadata}. The document is tagged with its
 *       {@value #SOURCE_METADATA_KEY} (where it came from) and {@value #CATEGORY_METADATA_KEY}
 *       (which source it belongs to, e.g. {@code trucks}).</li>
 *   <li>{@link EmbeddingStoreIngestor} runs the rest: a {@code DocumentTransformer} cleans the text,
 *       the {@code DocumentSplitter} cuts the document into {@link TextSegment}s (each copies the
 *       document's metadata, so every chunk carries the category), the {@link EmbeddingModel} embeds
 *       them in one batch, and {@code EmbeddingStore.addAll} stores vector + segment pairs.</li>
 * </ol>
 * The category tag is what lets {@code RetrievalAugmentorConfig} search each source separately with
 * a metadata filter, so one source can't crowd the other out of the top results.
 *
 * <p>Tika's HTML extraction leaves tabs, {@code \r\n} and long runs of blank lines; the transformer
 * collapses them, so chunks hold more text and embed less noise. Paragraph breaks stay, because the
 * splitter cuts on them first.
 *
 * <p>{@code DocumentSplitters.recursive} splits by paragraph, then line, sentence and word until each
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

    public static final String SOURCE_METADATA_KEY = "source";
    public static final String CATEGORY_METADATA_KEY = "category";

    private static final int MAX_SEGMENT_TOKENS = 256;
    private static final int OVERLAP_TOKENS = 32;

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreProperties embeddingStoreProperties;

    /**
     * Parses, cleans, splits, embeds and stores the documents of every source in
     * {@code ai.rag.sources}, tagging each chunk with its source's category.
     *
     * @param embeddingStore the store to write the segments into
     * @throws UncheckedIOException if a document cannot be read
     */
    public void ingest(EmbeddingStore<TextSegment> embeddingStore) {
        var parser = new ApacheTikaDocumentParser();
        var ingestor = EmbeddingStoreIngestor.builder()
                .documentTransformer(DocumentIngester::normalizeWhitespace)
                .documentSplitter(DocumentSplitters.recursive(MAX_SEGMENT_TOKENS, OVERLAP_TOKENS,
                        new HuggingFaceTokenCountEstimator()))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        embeddingStoreProperties.getSources().forEach((category, source) ->
                source.getDocuments().forEach(resource -> {
                    log.debug("Loading {} document: {}", category, resource.getDescription());
                    var document = parse(parser, resource);
                    document.metadata()
                            .put(SOURCE_METADATA_KEY, resource.getDescription())
                            .put(CATEGORY_METADATA_KEY, category);
                    ingestor.ingest(document);
                }));
    }

    private static Document normalizeWhitespace(Document document) {
        var text = document.text()
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
        return Document.from(text, document.metadata());
    }

    private Document parse(ApacheTikaDocumentParser parser, Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return parser.parse(inputStream);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource.getDescription(), e);
        }
    }
}
