package com.nazjara.configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.FinishReason;

/**
 * Streams the scripted rounds in order (repeating the last one), chunk by chunk through
 * {@code onPartialResponse(PartialResponse, PartialResponseContext)} like a real model. A round
 * ends with a tool call when it has one, otherwise with a plain answer. After the round's handle is
 * cancelled the model stops sending, as a dropped HTTP request would, and never completes.
 */
final class ScriptedStreamingChatModel implements StreamingChatModel {

    record Round(List<String> chunks, ToolExecutionRequest toolCall) {

        static Round text(String... chunks) {
            return new Round(List.of(chunks), null);
        }

        static Round textThenToolCall(ToolExecutionRequest toolCall, String... chunks) {
            return new Round(List.of(chunks), toolCall);
        }
    }

    final List<ChatRequest> requests = new CopyOnWriteArrayList<>();
    final List<String> sentChunks = new CopyOnWriteArrayList<>();
    final CountDownLatch cancelObserved = new CountDownLatch(1);
    private final List<Round> rounds;

    ScriptedStreamingChatModel(Round... rounds) {
        this.rounds = List.of(rounds);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        requests.add(request);
        var round = rounds.get(Math.min(requests.size(), rounds.size()) - 1);
        var handle = new FlagHandle();
        var context = new PartialResponseContext(handle);

        for (var chunk : round.chunks()) {
            if (handle.isCancelled()) {
                cancelObserved.countDown();
                return;
            }
            sentChunks.add(chunk);
            handler.onPartialResponse(new PartialResponse(chunk), context);
        }

        if (round.toolCall() != null) {
            if (handle.isCancelled()) {
                cancelObserved.countDown();
                return;
            }
            handler.onPartialToolCall(PartialToolCall.builder()
                    .index(0)
                    .id(round.toolCall().id())
                    .name(round.toolCall().name())
                    .partialArguments(round.toolCall().arguments())
                    .build(), new PartialToolCallContext(handle));
        }

        if (handle.isCancelled()) {
            cancelObserved.countDown();
            return;
        }
        var text = String.join("", round.chunks());
        var reply = round.toolCall() == null ? AiMessage.from(text)
                : text.isEmpty() ? AiMessage.from(round.toolCall())
                : AiMessage.from(text, List.of(round.toolCall()));
        handler.onCompleteResponse(ChatResponse.builder()
                .aiMessage(reply)
                .finishReason(round.toolCall() == null ? FinishReason.STOP : FinishReason.TOOL_EXECUTION)
                .build());
    }

    List<String> toolNames(int requestIndex) {
        return requests.get(requestIndex).toolSpecifications().stream().map(ToolSpecification::name).toList();
    }

    void awaitRequests(int count) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (requests.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    boolean awaitCancel() throws InterruptedException {
        return cancelObserved.await(5, TimeUnit.SECONDS);
    }

    private static final class FlagHandle implements StreamingHandle {

        private volatile boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
