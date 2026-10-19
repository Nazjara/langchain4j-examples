package com.nazjara.observation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Turns each retrieval into a {@code retrieve} span, using LangChain4j's
 * {@link ContentRetrieverListener} SPI ({@code @Experimental}, since 1.11.0). LangChain4j ships no
 * Micrometer implementation of it; only the chat model has one.
 *
 * <p><b>How it is attached.</b> {@code contentRetriever.addListener(listener)} wraps the retriever
 * in a {@code ListeningContentRetriever}, which calls {@code onRequest}, delegates, then calls
 * {@code onResponse} or {@code onError} with the same {@code attributes()} map. That map is how the
 * open observation travels from the start callback to the end callback. The span covers the whole
 * {@code EmbeddingStoreContentRetriever.retrieve}: embedding the question with the local model and
 * searching the store.
 *
 * <p><b>Nesting.</b> {@code DefaultRetrievalAugmentor} calls a single retriever for a single query
 * on the caller's thread, so this span lands under the AI Service span. With several queries
 * (query expansion) or several retrievers (a query router), it runs them on an executor; their
 * spans would then lose their parent unless the executor propagates the observation context.
 *
 * <p>Spring AI traces the same step without code: {@code QuestionAnswerAdvisor} gets its own
 * observation, and the embedding call inside it another.
 */
@RequiredArgsConstructor
public class RetrievalObservationListener implements ContentRetrieverListener {

    /** Observation name, and so the name of the timer metric Spring Boot derives from it. */
    public static final String RETRIEVAL = "langchain4j.retrieval";

    private static final String OPEN_OBSERVATION = RetrievalObservationListener.class.getName();

    private final ObservationRegistry observationRegistry;
    private final boolean includeContent;

    /**
     * Starts the {@code retrieve} observation and makes it current, so that observations started
     * inside the retriever become its children.
     *
     * @param requestContext the query and an attributes map shared with the end callback
     */
    @Override
    public void onRequest(ContentRetrieverRequestContext requestContext) {
        var observation = Observation.createNotStarted(RETRIEVAL, observationRegistry).contextualName("retrieve");
        if (includeContent) {
            observation.highCardinalityKeyValue("langchain4j.retrieval.query", requestContext.query().text());
        }
        requestContext.attributes().put(OPEN_OBSERVATION, OpenObservation.start(observation));
    }

    /**
     * Records how many chunks came back and, per chunk, its file, segment index and similarity
     * score, then stops the observation. These are high-cardinality key values: span attributes
     * only, never metric tags.
     *
     * @param responseContext the retrieved contents and the shared attributes map
     */
    @Override
    public void onResponse(ContentRetrieverResponseContext responseContext) {
        if (!(responseContext.attributes().remove(OPEN_OBSERVATION) instanceof OpenObservation open)) {
            return;
        }
        var contents = responseContext.contents();
        open.observation()
                .highCardinalityKeyValue("langchain4j.retrieval.count", String.valueOf(contents.size()))
                .highCardinalityKeyValue("langchain4j.retrieval.results", describe(contents));
        open.succeed();
    }

    /**
     * Marks the observation as failed and stops it.
     *
     * @param errorContext the failure and the shared attributes map
     */
    @Override
    public void onError(ContentRetrieverErrorContext errorContext) {
        if (errorContext.attributes().remove(OPEN_OBSERVATION) instanceof OpenObservation open) {
            open.fail(errorContext.error());
        }
    }

    private static String describe(List<Content> contents) {
        return contents.stream().map(RetrievalObservationListener::describe).collect(Collectors.joining(", "));
    }

    private static String describe(Content content) {
        var metadata = content.textSegment().metadata();
        var score = content.metadata().get(ContentMetadata.SCORE);
        return "%s#%s (score %s)".formatted(
                Objects.toString(metadata.getString(Document.FILE_NAME), "?"),
                Objects.toString(metadata.getString("index"), "?"),
                score instanceof Double value ? String.format(Locale.ROOT, "%.3f", value) : "?");
    }
}
