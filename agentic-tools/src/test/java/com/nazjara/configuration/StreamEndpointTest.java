package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.configuration.ScriptedStreamingChatModel.Round;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * {@code POST /ask/stream} over the embedded servlet container with a scripted streaming model:
 * two tool rounds without any text, then the answer, each model call answering later on another
 * thread like a real HTTP client does. Reads the raw event stream the way a client does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "ANTHROPIC_API_KEY=not-used")
class StreamEndpointTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void clientReceivesTheWholeToolLoopOverHttp() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ask/stream"))
                .header("Content-Type", "application/json")
                .header("X-Role", "MEMBER")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"Find a flight and convert the price\"}"))
                .build();

        var lines = HttpClient.newHttpClient().send(request, BodyHandlers.ofLines()).body().toList();

        var events = lines.stream().filter(line -> line.startsWith("event:")).map(line -> line.substring("event:".length())).toList();
        System.out.println("events over HTTP -> " + events);
        assertThat(events).containsExactly("tool-call", "tool-start", "tool-end", "tool-call", "tool-start", "tool-end", "text", "text", "done");
    }

    @TestConfiguration
    static class ScriptedModelConfig {

        @Bean
        @Primary
        StreamingChatModel scriptedStreamingChatModel() {
            var delegate = new ScriptedStreamingChatModel(
                    Round.textThenToolCall(call("call-1", "searchFlights", "{\"from\":\"Kyiv\",\"to\":\"Lisbon\",\"date\":\"2026-10-20\"}")),
                    Round.textThenToolCall(call("call-2", "convertCurrency", "{\"amount\":168,\"from\":\"EUR\",\"to\":\"UAH\"}")),
                    Round.text("The cheapest flight ", "costs 7593.60 UAH."));
            return new StreamingChatModel() {
                @Override
                public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                    CompletableFuture.runAsync(() -> {
                        pause();
                        delegate.doChat(request, handler);
                    });
                }
            };
        }

        private static void pause() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static ToolExecutionRequest call(String id, String name, String arguments) {
            return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
        }
    }
}
