package com.nazjara.configuration;

import static com.nazjara.bootstrap.DocumentIngester.CATEGORY_METADATA_KEY;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter.FallbackStrategy;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The two retrieval pipelines behind the AI Services: one source per {@code ai.rag.sources} entry,
 * with a different query router on each.
 *
 * <p><b>Why sources.</b> One search over all documents returns the N chunks most similar to the
 * question. "What is a good truck to pull a Sportsman 232 boat?" names a boat, so every top chunk
 * came from the boat bulletins and the truck list never reached Claude. Here every source is its own
 * {@link EmbeddingStoreContentRetriever} over the same store, restricted to its chunks by a metadata
 * filter on {@code category} (set at ingestion), with its own top {@code max-results-per-source}.
 * One source can no longer crowd another out.
 *
 * <p><b>The pipeline.</b> {@link DefaultRetrievalAugmentor} runs five replaceable steps before each
 * model call: query transformer (unchanged here) → <em>query router</em> → the chosen retrievers
 * (in parallel when there are several) → content aggregator (default: reciprocal-rank fusion of all
 * results, no cut-off) → content injector (default: appends the chunks to the user message). Only the
 * router differs between the two beans:
 * <ul>
 *   <li>{@code searchAllAugmentor}: {@link DefaultQueryRouter} sends every question to every source.
 *       No extra model call, and it can't miss a source. This is the fix for questions that need
 *       facts from several sources.</li>
 *   <li>{@code routingAugmentor}: {@link LanguageModelQueryRouter} first asks Claude which sources
 *       fit the question, listing each source's description as a numbered option; Claude answers
 *       with one or more numbers ("1" or "1,2"). It costs one extra short Claude call per question,
 *       and pays off when there are many sources and most questions need only some of them: fewer
 *       searches and fewer unrelated chunks in the prompt. If Claude's reply can't be parsed,
 *       {@link FallbackStrategy#ROUTE_TO_ALL} searches every source; a parseable but wrong pick is not
 *       caught.</li>
 * </ul>
 *
 * <p>Spring AI's {@code RetrievalAugmentationAdvisor} has the same pipeline idea (query transformers,
 * query expander, document joiner, query augmenter) but takes a single {@code DocumentRetriever}, so
 * it has no router: searching several sources needs a hand-written retriever.
 *
 * <p>The source retrievers are not beans, because their number comes from properties. So the
 * starter's {@code RagAutoConfiguration} (imported by its main auto-configuration, so it can't be
 * excluded on its own) still registers its own retriever over the whole store, as it does whenever no
 * {@code ContentRetriever} bean exists. Nothing uses it: both AI Services use {@code EXPLICIT} wiring
 * and name their augmentor.
 */
@Configuration
@RequiredArgsConstructor
public class RetrievalAugmentorConfig {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStoreProperties embeddingStoreProperties;

    @Bean
    RetrievalAugmentor searchAllAugmentor() {
        return DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(sourceRetrievers().keySet()))
                .build();
    }

    @Bean
    RetrievalAugmentor routingAugmentor(ChatModel chatModel) {
        return DefaultRetrievalAugmentor.builder()
                .queryRouter(LanguageModelQueryRouter.builder()
                        .chatModel(chatModel)
                        .retrieverToDescription(sourceRetrievers())
                        .fallbackStrategy(FallbackStrategy.ROUTE_TO_ALL)
                        .build())
                .build();
    }

    private Map<ContentRetriever, String> sourceRetrievers() {
        var retrievers = new LinkedHashMap<ContentRetriever, String>();
        embeddingStoreProperties.getSources().forEach((category, source) -> retrievers.put(
                EmbeddingStoreContentRetriever.builder()
                        .displayName(category)
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .filter(metadataKey(CATEGORY_METADATA_KEY).isEqualTo(category))
                        .maxResults(embeddingStoreProperties.getMaxResultsPerSource())
                        .build(),
                source.getDescription()));
        return retrievers;
    }
}
