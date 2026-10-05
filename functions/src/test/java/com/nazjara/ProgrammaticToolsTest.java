package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The tool loop without Spring, without {@code @Tool} and without an API key.
 *
 * <p>The tool is declared programmatically: a {@link ToolSpecification} (name, description, JSON
 * schema) paired with a {@link ToolExecutor} that receives the raw JSON arguments. This is the
 * analogue of Spring AI's {@code FunctionToolCallback.builder(name, function).inputType(...)}, and
 * the form to use when tools are only known at runtime. The model is a stub that scripts the replies
 * a real model would send, so every step of the loop can be asserted.
 */
class ProgrammaticToolsTest {

    private static final ToolSpecification CURRENT_WEATHER = ToolSpecification.builder()
            .name("currentWeather")
            .description("Get a current weather for a location")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city", "City name")
                    .addStringProperty("country", "Country name")
                    .required("city", "country")
                    .build())
            .build();

    private static final ToolExecutionRequest LVIV_REQUEST = ToolExecutionRequest.builder()
            .id("call-1")
            .name("currentWeather")
            .arguments("{\"city\":\"Lviv\",\"country\":\"Ukraine\"}")
            .build();

    interface WeatherAssistant {

        Result<String> ask(String question);
    }

    @Test
    void toolResultIsSentBackToModelBeforeFinalAnswer() {
        var model = new ScriptedChatModel(AiMessage.from(LVIV_REQUEST), AiMessage.from("It is 12°C in Lviv."));
        var executedArguments = new ArrayList<String>();
        ToolExecutor executor = (request, memoryId) -> {
            executedArguments.add(request.arguments());
            return "{\"temp\":12}";
        };
        var assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(model)
                .tools(Map.of(CURRENT_WEATHER, executor))
                .build();

        var result = assistant.ask("Weather in Lviv?");

        assertThat(result.content()).isEqualTo("It is 12°C in Lviv.");
        assertThat(executedArguments).containsExactly(LVIV_REQUEST.arguments());
        assertThat(model.requests).hasSize(2);
        assertThat(model.requests.getFirst().toolSpecifications()).containsExactly(CURRENT_WEATHER);
        assertThat(model.requests.getLast().messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                        message -> assertThat(message.text()).isEqualTo("{\"temp\":12}"));
        assertThat(result.toolExecutions()).singleElement()
                .satisfies(execution -> assertThat(execution.result()).isEqualTo("{\"temp\":12}"));
    }

    @Test
    void executionErrorIsTurnedIntoToolResultByErrorHandler() {
        var model = new ScriptedChatModel(AiMessage.from(LVIV_REQUEST), AiMessage.from("Weather is unavailable."));
        ToolExecutor failingExecutor = (request, memoryId) -> {
            throw new IllegalStateException("API Ninjas returned 502");
        };
        var assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(model)
                .tools(Map.of(CURRENT_WEATHER, failingExecutor))
                .toolExecutionErrorHandler((error, context) ->
                        ToolErrorHandlerResult.text("Weather service unavailable, try again later."))
                .build();

        var result = assistant.ask("Weather in Lviv?");

        assertThat(result.content()).isEqualTo("Weather is unavailable.");
        assertThat(model.requests.getLast().messages().getLast())
                .isInstanceOfSatisfying(ToolExecutionResultMessage.class,
                        message -> assertThat(message.text()).isEqualTo("Weather service unavailable, try again later."));
        assertThat(result.toolExecutions()).singleElement()
                .satisfies(execution -> assertThat(execution.hasFailed()).isTrue());
    }

    @Test
    void loopStopsWhenMaxToolCallingRoundTripsIsExceeded() {
        var model = new ScriptedChatModel(AiMessage.from(LVIV_REQUEST));
        ToolExecutor executor = (request, memoryId) -> "{\"temp\":12}";
        var assistant = AiServices.builder(WeatherAssistant.class)
                .chatModel(model)
                .tools(Map.of(CURRENT_WEATHER, executor))
                .maxToolCallingRoundTrips(3)
                .build();

        assertThatThrownBy(() -> assistant.ask("Weather in Lviv?"))
                .hasMessageContaining("exceeded 3 tool calling round trips");
        assertThat(model.requests).hasSize(4);
    }

    /**
     * Returns the scripted replies in order, repeating the last one, and records every request.
     */
    private static final class ScriptedChatModel implements ChatModel {

        private final List<AiMessage> replies;
        private final List<ChatRequest> requests = new ArrayList<>();

        private ScriptedChatModel(AiMessage... replies) {
            this.replies = List.of(replies);
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            var reply = replies.get(Math.min(requests.size(), replies.size()) - 1);
            return ChatResponse.builder().aiMessage(reply).build();
        }
    }
}
