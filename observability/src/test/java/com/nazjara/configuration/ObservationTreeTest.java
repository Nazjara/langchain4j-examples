package com.nazjara.configuration;

import static com.nazjara.configuration.RecordingObservationHandler.high;
import static com.nazjara.configuration.RecordingObservationHandler.low;
import static com.nazjara.configuration.RecordingObservationHandler.parentName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nazjara.observation.AiServiceObservations;
import com.nazjara.observation.RetrievalObservationListener;
import com.nazjara.service.CafeAssistant;
import com.nazjara.tool.CafeTools;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservationTreeTest {

    private static final String HTTP = "http post /ask";
    private static final String SERVICE = "ai_service CafeAssistant.ask";
    private static final String CHAT = "chat unknown";
    private static final String TOOL = "execute_tool getTodaysSpecial";
    private static final AiMessage TOOL_CALL = AiMessage.from(ToolExecutionRequest.builder()
            .id("call-1").name("getTodaysSpecial").arguments("{}").build());
    private static final AiMessage ANSWER = AiMessage.from("Today's special is honey cake.");

    private final RecordingObservationHandler handler = new RecordingObservationHandler();
    private final ObservationRegistry observationRegistry = ObservationRegistry.create();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        observationRegistry.observationConfig()
                .observationHandler(handler)
                .observationHandler(new DefaultMeterObservationHandler(meterRegistry));
    }

    @Test
    void everyStepOfTheCallNestsUnderTheAiServiceSpan() {
        var assistant = assistant(model(TOOL_CALL, ANSWER), new CafeTools(), true);

        var result = insideHttpRequest(() -> assistant.ask("What's today's special?"));

        assertThat(result.content()).isEqualTo(ANSWER.text());
        assertThat(handler.contextualNames()).containsExactly("retrieve", CHAT, TOOL, CHAT, SERVICE, HTTP);
        assertThat(parentName(handler.named(SERVICE))).isEqualTo(HTTP);
        assertThat(handler.stopped).filteredOn(context -> !List.of(SERVICE, HTTP).contains(context.getContextualName()))
                .allSatisfy(context -> assertThat(parentName(context)).isEqualTo(SERVICE));
        assertThat(observationRegistry.getCurrentObservation()).isNull();
    }

    @Test
    void identifiersBecomeTagsAndDetailsStayOnTheSpan() {
        var assistant = assistant(model(TOOL_CALL, ANSWER), new CafeTools(), true);

        insideHttpRequest(() -> assistant.ask("What's today's special?"));

        var service = handler.named(SERVICE);
        assertThat(low(service, "langchain4j.ai_service.name")).isEqualTo("CafeAssistant");
        assertThat(low(service, "langchain4j.ai_service.method")).isEqualTo("ask");
        assertThat(low(service, "outcome")).isEqualTo("SUCCESS");
        assertThat(high(service, "langchain4j.invocation.id")).isNotBlank();
        var retrieval = handler.named("retrieve");
        assertThat(high(retrieval, "langchain4j.retrieval.count")).isEqualTo("1");
        assertThat(high(retrieval, "langchain4j.retrieval.results")).isEqualTo("cafe.md#2 (score 0.812)");
        var tool = handler.named(TOOL);
        assertThat(low(tool, "gen_ai.tool.name")).isEqualTo("getTodaysSpecial");
        assertThat(low(tool, "outcome")).isEqualTo("SUCCESS");
        assertThat(high(tool, "gen_ai.tool.call.id")).isEqualTo("call-1");
        assertThat(high(handler.named(CHAT), "input_tokens")).isEqualTo(String.valueOf(ScriptedChatModel.INPUT_TOKENS));
        assertThat(meterRegistry.get(AiServiceObservations.TOOL).tag("gen_ai.tool.name", "getTodaysSpecial").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get(AiServiceObservations.TOOL).timer().getId().getTag("gen_ai.tool.call.id")).isNull();
    }

    @Test
    void contentIsOnTheSpansOnlyWhenEnabled() {
        var withContent = new RecordingObservationHandler();
        var withoutContent = new RecordingObservationHandler();

        record(withContent, true);
        record(withoutContent, false);

        assertThat(high(withContent.named(SERVICE), "langchain4j.user_message")).isEqualTo("What's today's special?");
        assertThat(high(withContent.named(SERVICE), "langchain4j.answer")).isEqualTo(ANSWER.text());
        assertThat(high(withContent.named("retrieve"), "langchain4j.retrieval.query")).isEqualTo("What's today's special?");
        assertThat(high(withContent.named(TOOL), "gen_ai.tool.call.arguments")).isEqualTo("{}");
        assertThat(high(withContent.named(TOOL), "gen_ai.tool.call.result")).startsWith("Today's special");
        assertThat(withoutContent.stopped).allSatisfy(context -> assertThat(context.getHighCardinalityKeyValues())
                .extracting(keyValue -> keyValue.getKey())
                .doesNotContain("langchain4j.user_message", "langchain4j.answer", "langchain4j.retrieval.query",
                        "gen_ai.tool.call.arguments", "gen_ai.tool.call.result"));
    }

    @Test
    void tokenUsageIsRecordedForEveryModelCall() {
        var assistant = assistant(model(TOOL_CALL, ANSWER), new CafeTools(), false);

        insideHttpRequest(() -> assistant.ask("What's today's special?"));

        var input = meterRegistry.get("gen_ai.client.token.usage").tag("gen_ai.token.type", "input").summary();
        assertThat(input.count()).isEqualTo(2);
        assertThat(input.totalAmount()).isEqualTo(2.0 * ScriptedChatModel.INPUT_TOKENS);
        assertThat(meterRegistry.get("gen_ai.client.operation.duration").timer().count()).isEqualTo(2);
    }

    @Test
    void failedModelCallEndsTheAiServiceSpanAsError() {
        var assistant = assistant(model(), new CafeTools(), false);

        assertThatThrownBy(() -> insideHttpRequest(() -> assistant.ask("What's today's special?")))
                .hasMessageContaining("model unavailable");

        assertThat(handler.contextualNames()).containsExactly("retrieve", CHAT, SERVICE, HTTP);
        assertThat(low(handler.named(CHAT), "outcome")).isEqualTo("ERROR");
        assertThat(low(handler.named(SERVICE), "outcome")).isEqualTo("ERROR");
        assertThat(handler.named(SERVICE).getError()).hasMessageContaining("model unavailable");
        assertThat(observationRegistry.getCurrentObservation()).isNull();
    }

    @Test
    void failingToolEndsItsSpanAsErrorAndTheModelSeesTheError() {
        var model = model(TOOL_CALL, ANSWER);
        var assistant = assistant(model, new FailingCafeTools(), false);

        var result = insideHttpRequest(() -> assistant.ask("What's today's special?"));

        assertThat(result.content()).isEqualTo(ANSWER.text());
        assertThat(low(handler.named(TOOL), "outcome")).isEqualTo("ERROR");
        assertThat(low(handler.named(SERVICE), "outcome")).isEqualTo("SUCCESS");
        assertThat(model.requests.get(1).messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class, message -> assertThat(message.text()).contains("kitchen closed"));
    }

    @Test
    void toolErrorThatEscapesTheLoopStillClosesTheToolSpan() {
        var observations = new AiServiceObservations(observationRegistry, false);
        var assistant = AiServices.builder(CafeAssistant.class)
                .chatModel(model(TOOL_CALL, ANSWER))
                .tools(new FailingCafeTools())
                .toolExecutionErrorHandler((error, context) -> {
                    throw new IllegalStateException("tool failed", error);
                })
                .beforeToolExecution(observations::beforeTool)
                .afterToolExecution(observations::afterTool)
                .registerListeners(observations.listeners())
                .build();

        assertThatThrownBy(() -> insideHttpRequest(() -> assistant.ask("What's today's special?")))
                .hasMessageContaining("tool failed");

        assertThat(handler.contextualNames()).containsExactly(CHAT, TOOL, SERVICE, HTTP);
        assertThat(low(handler.named(TOOL), "outcome")).isEqualTo("ERROR");
        assertThat(low(handler.named(SERVICE), "outcome")).isEqualTo("ERROR");
        assertThat(observationRegistry.getCurrentObservation()).isNull();
    }

    private void record(RecordingObservationHandler recorder, boolean includeContent) {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(recorder);
        var model = new ScriptedChatModel(new ObservationChatModelListener(registry, meterRegistry), TOOL_CALL, ANSWER);
        new AiServicesConfig()
                .cafeAssistant(model, retriever(registry, includeContent), new CafeTools(), new AiServiceObservations(registry, includeContent))
                .ask("What's today's special?");
    }

    private CafeAssistant assistant(ChatModel model, CafeTools tools, boolean includeContent) {
        return new AiServicesConfig().cafeAssistant(model, retriever(observationRegistry, includeContent), tools,
                new AiServiceObservations(observationRegistry, includeContent));
    }

    private ScriptedChatModel model(AiMessage... replies) {
        return new ScriptedChatModel(new ObservationChatModelListener(observationRegistry, meterRegistry), replies);
    }

    private static ContentRetriever retriever(ObservationRegistry registry, boolean includeContent) {
        ContentRetriever retriever = query -> List.of(Content.from(
                TextSegment.from("Breakfast is served until 12:00 every day.",
                        Metadata.from(Document.FILE_NAME, "cafe.md").put("index", "2")),
                Map.of(ContentMetadata.SCORE, 0.8123)));
        return retriever.addListener(new RetrievalObservationListener(registry, includeContent));
    }

    private <T> T insideHttpRequest(Supplier<T> call) {
        var http = Observation.createNotStarted("http.server.requests", observationRegistry).contextualName(HTTP).start();
        try (var ignored = http.openScope()) {
            return call.get();
        } finally {
            http.stop();
        }
    }

    static final class FailingCafeTools extends CafeTools {

        @Override
        @Tool("Get today's special dish at Spring Brew Café")
        public String getTodaysSpecial() {
            throw new IllegalStateException("kitchen closed");
        }
    }
}
