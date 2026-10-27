package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.model.Role;
import com.nazjara.model.StreamEvent;
import com.nazjara.configuration.ScriptedStreamingChatModel.Round;
import com.nazjara.rest.TravelStreamEvents;
import com.nazjara.service.StreamingTravelAssistant;
import com.nazjara.tool.RoleToolProvider;
import com.nazjara.tool.TravelTools;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The SSE events and the cancellation of {@link TravelStreamEvents}, against a scripted streaming
 * model and a recording emitter, without an API key or a servlet container.
 */
class TravelStreamEventsTest {

    private static final String QUESTION = "Find me a flight from Kyiv to Lisbon on 2026-10-20";
    private static final String SEARCH_ARGUMENTS = "{\"from\":\"Kyiv\",\"to\":\"Lisbon\",\"date\":\"2026-10-20\"}";

    private final AiServicesConfig config = new AiServicesConfig(
            new AgenticToolsProperties(10, new AgenticToolsProperties.Plan(100, 3)));

    @Test
    void streamsTextAndToolEventsInOrder() throws Exception {
        var model = new ScriptedStreamingChatModel(
                Round.textThenToolCall(searchFlights(), "Let me ", "check. "),
                Round.text("Two ", "flights."));
        var emitter = new RecordingEmitter(Integer.MAX_VALUE);

        TravelStreamEvents.attach(assistant(model).ask(QUESTION, parameters(Role.MEMBER)), emitter);
        emitter.awaitCompleted();

        System.out.println("events -> " + emitter.names);
        assertThat(emitter.names).containsExactly("text", "text", "tool-call", "tool-start", "tool-end", "text", "text", "done");
        assertThat(emitter.payloads.get(0)).isEqualTo(new StreamEvent.Text("Let me "));
        assertThat(emitter.payloads.get(3)).isEqualTo(new StreamEvent.ToolStarted("searchFlights", SEARCH_ARGUMENTS));
        assertThat(emitter.payloads.get(4)).isInstanceOfSatisfying(StreamEvent.ToolFinished.class, finished -> {
            assertThat(finished.name()).isEqualTo("searchFlights");
            assertThat(finished.failed()).isFalse();
            assertThat(finished.result()).contains("Kyiv", "Lisbon");
        });
        assertThat(emitter.payloads.getLast()).isEqualTo(new StreamEvent.Done("STOP"));
    }

    @Test
    void streamingGuestIsNotOfferedTheBookingTools() throws Exception {
        var model = new ScriptedStreamingChatModel(Round.text("Hello."));
        var emitter = new RecordingEmitter(Integer.MAX_VALUE);

        TravelStreamEvents.attach(assistant(model).ask(QUESTION, parameters(Role.GUEST)), emitter);
        emitter.awaitCompleted();

        assertThat(model.toolNames(0)).hasSize(13).doesNotContain("bookFlight", "bookHotel");
    }

    @Test
    void clientDisconnectCancelsTheStreamingModelCall() throws Exception {
        var chunks = IntStream.range(0, 20).mapToObj(index -> "chunk-" + index + " ").toArray(String[]::new);
        var model = new ScriptedStreamingChatModel(Round.text(chunks));
        var emitter = new RecordingEmitter(3);

        TravelStreamEvents.attach(assistant(model).ask(QUESTION, parameters(Role.MEMBER)), emitter);

        assertThat(model.awaitCancel()).isTrue();
        System.out.println("chunks sent before the model call stopped -> " + model.sentChunks.size());
        assertThat(model.sentChunks).hasSize(3);
        assertThat(emitter.names).containsExactly("text", "text");
    }

    @Test
    void cancelAfterTheModelCallFinishedDoesNotStopTheNextCallFromStarting() throws Exception {
        var model = new ScriptedStreamingChatModel(
                Round.textThenToolCall(searchFlights(), "Checking. "),
                Round.text("Done.", " Really."));
        var emitter = new RecordingEmitter(3);

        TravelStreamEvents.attach(assistant(model).ask(QUESTION, parameters(Role.MEMBER)), emitter);

        assertThat(model.awaitCancel()).isTrue();
        System.out.println("model calls after the client was gone -> " + model.requests.size());
        assertThat(emitter.names).containsExactly("text", "tool-call");
        assertThat(model.requests).hasSize(2);
        assertThat(model.sentChunks).containsExactly("Checking. ", "Done.");
    }

    @Test
    void clientGoneDuringABareToolCallRoundStopsTheLoopAtTheNextCall() throws Exception {
        var model = new ScriptedStreamingChatModel(
                Round.textThenToolCall(searchFlights()),
                Round.textThenToolCall(searchFlights()),
                Round.text("Never sent."));
        var emitter = new RecordingEmitter(2);

        TravelStreamEvents.attach(assistant(model).ask(QUESTION, parameters(Role.MEMBER)), emitter);

        assertThat(model.awaitCancel()).isTrue();
        System.out.println("model calls after the client left during a bare tool call -> " + model.requests.size());
        assertThat(emitter.names).containsExactly("tool-call");
        assertThat(model.requests).hasSize(2);
        assertThat(model.sentChunks).isEmpty();
    }

    private StreamingTravelAssistant assistant(ScriptedStreamingChatModel model) {
        return config.streamingAssistant(model, new RoleToolProvider(new TravelTools()));
    }

    private static InvocationParameters parameters(Role role) {
        return InvocationParameters.from(Map.of(TravelTools.USER_ID, "alice", RoleToolProvider.ROLE, role));
    }

    private static ToolExecutionRequest searchFlights() {
        return ToolExecutionRequest.builder().id("call-1").name("searchFlights").arguments(SEARCH_ARGUMENTS).build();
    }

    private static final class RecordingEmitter extends SseEmitter {

        final List<String> names = new CopyOnWriteArrayList<>();
        final List<StreamEvent> payloads = new CopyOnWriteArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicInteger sends = new AtomicInteger();
        private final int failFromSend;

        RecordingEmitter(int failFromSend) {
            this.failFromSend = failFromSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (sends.incrementAndGet() >= failFromSend) {
                throw new IOException("Broken pipe");
            }
            for (var part : builder.build()) {
                if (part.getData() instanceof String line && line.startsWith("event:")) {
                    names.add(line.substring("event:".length(), line.indexOf('\n')));
                } else if (part.getData() instanceof StreamEvent event) {
                    payloads.add(event);
                }
            }
        }

        @Override
        public void complete() {
            completed.countDown();
        }

        void awaitCompleted() throws InterruptedException {
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
