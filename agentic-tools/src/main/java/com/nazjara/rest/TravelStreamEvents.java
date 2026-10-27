package com.nazjara.rest;

import com.nazjara.model.StreamEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.service.TokenStream;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Connects a {@link TokenStream} to a server-sent-events response. Each callback becomes one
 * named SSE event with a JSON body ({@link StreamEvent}): {@code text} per chunk,
 * {@code tool-call} per fragment of a tool call's arguments as the model writes them, {@code tool-start} and {@code tool-end} around each tool call, then {@code done} or
 * {@code error}.
 *
 * <p><b>Cancellation.</b> The partial-response and partial-tool-call callbacks hand over a
 * {@link StreamingHandle} for the model call that is streaming. When the client goes away (a failed
 * write, or the emitter completing, timing out or erroring before the stream finished), that handle
 * is cancelled and the HTTP request to Anthropic is dropped. That is what {@code Flux<String>} from
 * {@code langchain4j-reactor} does not do. A handle belongs to one model call, and LangChain4j's
 * streaming handler does not check it between round trips, so a disconnect noticed while a tool runs
 * cannot stop the next model call from starting. The stream therefore remembers that the client is
 * gone and cancels each later call at its first chunk, text or tool call, so at most one more call
 * is started, not the rest of the loop. Models often open with a bare tool call and no text, which
 * is why partial tool calls are tracked too.
 *
 * <p><b>Noticing the disconnect.</b> The server only learns that the client left from a failed write.
 * A tool-call round would write nothing until the model has finished it, and the next model call
 * would already be running by then, so each fragment of a tool call is also sent as a
 * {@code tool-call} event. The first write after a close can still succeed, so a few fragments pass
 * before the stream is cancelled.
 */
@Slf4j
@UtilityClass
public class TravelStreamEvents {

    /**
     * Registers the callbacks and starts the stream.
     *
     * @param tokenStream the stream returned by {@code StreamingTravelAssistant.ask}, not started yet
     * @param emitter the response to write the events to
     */
    public void attach(TokenStream tokenStream, SseEmitter emitter) {
        var handle = new AtomicReference<StreamingHandle>();
        var finished = new AtomicBoolean();
        var clientGone = new AtomicBoolean();
        Runnable cancel = () -> cancelUnlessFinished(handle.get(), finished);
        Consumer<StreamingHandle> track = modelCall -> {
            handle.set(modelCall);
            if (clientGone.get()) {
                cancel.run();
            }
        };
        Runnable gone = () -> {
            clientGone.set(true);
            cancel.run();
        };

        emitter.onCompletion(gone);
        emitter.onTimeout(gone);
        emitter.onError(_ -> gone.run());

        tokenStream
                .onPartialResponseWithContext((partial, context) -> {
                    track.accept(context.streamingHandle());
                    send(emitter, gone, "text", new StreamEvent.Text(partial.text()));
                })
                .onPartialToolCallWithContext((partial, context) -> {
                    track.accept(context.streamingHandle());
                    send(emitter, gone, "tool-call", new StreamEvent.ToolCallDelta(partial.id(), partial.name(), partial.partialArguments()));
                })
                .beforeToolExecution(before -> send(emitter, gone, "tool-start",
                        new StreamEvent.ToolStarted(before.request().name(), before.request().arguments())))
                .onToolExecuted(execution -> send(emitter, gone, "tool-end",
                        new StreamEvent.ToolFinished(execution.request().name(), execution.result(), execution.hasFailed())))
                .onCompleteResponse(response -> {
                    finished.set(true);
                    send(emitter, gone, "done", new StreamEvent.Done(String.valueOf(response.finishReason())));
                    emitter.complete();
                })
                .onError(error -> {
                    finished.set(true);
                    log.warn("Stream failed", error);
                    send(emitter, gone, "error", new StreamEvent.Failed(error.getMessage()));
                    emitter.complete();
                })
                .start();
    }

    private void send(SseEmitter emitter, Runnable gone, String name, StreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(name).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("Client is gone, cancelling the stream: {}", e.toString());
            gone.run();
        }
    }

    private void cancelUnlessFinished(StreamingHandle handle, AtomicBoolean finished) {
        if (handle == null || finished.get()) {
            return;
        }
        try {
            handle.cancel();
        } catch (UnsupportedFeatureException e) {
            log.warn("The streaming model cannot cancel: {}", e.getMessage());
        }
    }
}
