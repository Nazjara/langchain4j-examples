package com.nazjara.configuration;

import static com.nazjara.bootstrap.DocumentIngester.CATEGORY_METADATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.service.RagAssistant;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalAugmentorConfigTest {

    private static final String QUESTION = "What is a good truck to pull a Sportsman 232 boat?";
    private static final List<String> BOAT_TEXTS = List.of(
            "The Sportsman Open 232 boat has a tested weight of 5,001 pounds.",
            "The Sportsman Open 232 boat with a Yamaha F300 reaches 52 miles per hour.",
            "The Sportsman Open 232 boat burns 9.8 gallons per hour at cruising speed.",
            "The Sportsman Open 232 boat is 23 feet long with an 8.5 foot beam.");
    private static final String TRUCK_TEXT = "The Chevy Colorado costs $55,000 dollars, and tow boats weighing up to 7,000 pounds.";

    private static EmbeddingModel embeddingModel;

    private InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private RetrievalAugmentorConfig config;
    private ReplyingChatModel answerModel;

    @BeforeAll
    static void loadEmbeddingModel() {
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    }

    @BeforeEach
    void setUp() {
        embeddingStore = new InMemoryEmbeddingStore<>();
        BOAT_TEXTS.forEach(text -> add(text, "boats"));
        add(TRUCK_TEXT, "trucks");

        var properties = new EmbeddingStoreProperties();
        properties.setMaxResultsPerSource(3);
        properties.getSources().put("trucks", source("Tow vehicles: truck prices and towing capacities"));
        properties.getSources().put("boats", source("Boats: models, weights and speeds"));
        config = new RetrievalAugmentorConfig(embeddingStore, embeddingModel, properties);
        answerModel = new ReplyingChatModel("Chevy Colorado");
    }

    @Test
    void singleSearchOverAllDocumentsMissesTheTruck() {
        var retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .build();

        var contents = retriever.retrieve(Query.from(QUESTION));

        assertThat(categories(contents)).containsOnly("boats");
    }

    @Test
    void searchAllAugmentorBringsTheTruckToClaude() {
        var result = ask(config.searchAllAugmentor());

        assertThat(categories(result.sources())).containsExactlyInAnyOrder("trucks", "boats", "boats", "boats");
        assertThat(answerModel.prompts).singleElement().asString().contains(TRUCK_TEXT);
    }

    @Test
    void routingAugmentorSearchesOnlyTheSourceClaudePicks() {
        var routerModel = new ReplyingChatModel("1");

        var result = ask(config.routingAugmentor(routerModel));

        assertThat(categories(result.sources())).containsExactly("trucks");
        assertThat(routerModel.prompts).singleElement().asString()
                .contains("Tow vehicles: truck prices and towing capacities")
                .contains("Boats: models, weights and speeds")
                .contains(QUESTION);
    }

    @Test
    void routingAugmentorSearchesBothSourcesWhenClaudePicksBoth() {
        var result = ask(config.routingAugmentor(new ReplyingChatModel("1, 2")));

        assertThat(categories(result.sources())).contains("trucks", "boats");
    }

    @Test
    void routingAugmentorSearchesAllSourcesWhenReplyIsUnusable() {
        var result = ask(config.routingAugmentor(new ReplyingChatModel("Both of them, I think.")));

        assertThat(categories(result.sources())).contains("trucks", "boats");
    }

    private void add(String text, String category) {
        var segment = TextSegment.from(text, Metadata.from(Map.of(CATEGORY_METADATA_KEY, category)));
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
    }

    private Result<String> ask(RetrievalAugmentor retrievalAugmentor) {
        return AiServices.builder(RagAssistant.class)
                .chatModel(answerModel)
                .retrievalAugmentor(retrievalAugmentor)
                .build()
                .ask(QUESTION);
    }

    private static EmbeddingStoreProperties.Source source(String description) {
        var source = new EmbeddingStoreProperties.Source();
        source.setDescription(description);
        return source;
    }

    private static List<String> categories(List<Content> contents) {
        return contents.stream()
                .map(content -> content.textSegment().metadata().getString(CATEGORY_METADATA_KEY))
                .toList();
    }

    private static final class ReplyingChatModel implements ChatModel {

        private final List<String> prompts = new ArrayList<>();
        private final String reply;

        private ReplyingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            prompts.add(((UserMessage) request.messages().getLast()).singleText());
            return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
        }
    }
}
