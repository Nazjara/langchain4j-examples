package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.AnswerWithToolCalls;
import com.nazjara.model.Question;
import com.nazjara.model.ToolCall;
import com.nazjara.service.WeatherAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the tool-calling example. One HTTP request may cause several model calls:
 * the {@link WeatherAssistant} proxy runs the tool loop before returning.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final WeatherAssistant weatherAssistant;

    /**
     * Answers a weather question using the weather tool.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's answer
     */
    @PostMapping("/weather")
    public Answer askQuestion(@RequestBody Question question) {
        return new Answer(weatherAssistant.ask(question.question()));
    }

    /**
     * Same as {@link #askQuestion(Question)}, plus every tool call the loop made (LangChain4j-only
     * endpoint, shows {@code Result.toolExecutions()}).
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's answer and the tool calls behind it
     */
    @PostMapping("/weather/tool-calls")
    public AnswerWithToolCalls askQuestionWithToolCalls(@RequestBody Question question) {
        var result = weatherAssistant.askWithToolCalls(question.question());
        var toolCalls = result.toolExecutions().stream()
                .map(execution -> new ToolCall(execution.request().name(), execution.request().arguments(),
                        execution.result(), execution.hasFailed()))
                .toList();
        return new AnswerWithToolCalls(result.content(), toolCalls);
    }
}
