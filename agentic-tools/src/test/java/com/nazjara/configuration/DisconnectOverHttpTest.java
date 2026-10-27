package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * A client that closes the connection after the first {@code tool-end}, over a real socket. The
 * scripted model streams like Claude does: bare tool calls whose arguments arrive in many small
 * fragments, then a text answer. Counts the model calls that still start after the client left.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ANTHROPIC_API_KEY=not-used")
class DisconnectOverHttpTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    SlowToolCallModel model;

    @Test
    void closingTheConnectionAfterTheFirstToolEndStopsTheLoopAtTheNextCall() throws Exception {
        var body = "{\"question\":\"Find a flight and convert the price\"}";
        try (var socket = new Socket("localhost", port)) {
            socket.getOutputStream().write(("POST /ask/stream HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                    + "X-Role: MEMBER\r\nConnection: close\r\nContent-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                    + "\r\n\r\n" + body).getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null && !line.equals("event:tool-end")) {
                continue;
            }
        }

        var settled = 0;
        var last = -1;
        for (var waited = 0; waited < 100 && settled < 20; waited++) {
            Thread.sleep(100);
            var now = model.requests.get();
            settled = now == last ? settled + 1 : 0;
            last = now;
        }
        System.out.println("model calls in total after closing the connection at the first tool-end -> " + model.requests.get());
        assertThat(model.requests.get()).isLessThanOrEqualTo(2);
    }

    @TestConfiguration
    static class ModelConfig {

        @Bean
        @Primary
        SlowToolCallModel slowToolCallModel() {
            return new SlowToolCallModel();
        }
    }

    static final class SlowToolCallModel implements StreamingChatModel {

        final AtomicInteger requests = new AtomicInteger();

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            var call = requests.incrementAndGet();
            CompletableFuture.runAsync(() -> stream(call, handler));
        }

        private void stream(int call, StreamingChatResponseHandler handler) {
            var cancelled = new boolean[1];
            StreamingHandle handle = new StreamingHandle() {
                @Override
                public void cancel() {
                    cancelled[0] = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            };
            pause(150);
            if (call >= 3) {
                for (var chunk : new String[] {"The cheapest ", "flight ", "costs 7593.60 UAH."}) {
                    if (cancelled[0]) {
                        return;
                    }
                    handler.onPartialResponse(new PartialResponse(chunk), new PartialResponseContext(handle));
                    pause(30);
                }
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("done")).finishReason(FinishReason.STOP).build());
                return;
            }
            var toolCall = ToolExecutionRequest.builder().id("call-" + call)
                    .name(call == 1 ? "searchFlights" : "convertCurrency")
                    .arguments(call == 1 ? "{\"from\":\"Kyiv\",\"to\":\"Lisbon\",\"date\":\"2026-10-20\"}"
                            : "{\"amount\":168,\"from\":\"EUR\",\"to\":\"UAH\"}")
                    .build();
            for (var index = 0; index < 12; index++) {
                if (cancelled[0]) {
                    return;
                }
                handler.onPartialToolCall(PartialToolCall.builder().index(0).id(toolCall.id()).name(toolCall.name())
                        .partialArguments("x").build(), new PartialToolCallContext(handle));
                pause(40);
            }
            if (cancelled[0]) {
                return;
            }
            handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).finishReason(FinishReason.TOOL_EXECUTION).build());
        }

        private static void pause(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
