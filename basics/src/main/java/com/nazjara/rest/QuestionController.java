package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.AnswerWithMetadata;
import com.nazjara.model.CapitalDetails;
import com.nazjara.model.GetCapitalResponse;
import com.nazjara.model.Question;
import com.nazjara.service.Assistant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the basics examples. Each endpoint is one model call made through the
 * {@link Assistant} AI Service proxy.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final Assistant assistant;

    /**
     * Free-text question and answer.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's reply
     */
    @PostMapping("/ask")
    public Answer ask(@RequestBody Question question) {
        return new Answer(assistant.ask(question.question()));
    }

    /**
     * Free-text question and answer plus token usage and finish reason (LangChain4j-only
     * endpoint, shows {@code Result<T>}).
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's reply and call metadata
     */
    @PostMapping("/ask/result")
    public AnswerWithMetadata askWithMetadata(@RequestBody Question question) {
        var result = assistant.askWithMetadata(question.question());
        var usage = result.tokenUsage();
        return new AnswerWithMetadata(result.content(), usage.inputTokenCount(), usage.outputTokenCount(),
                result.finishReason().name());
    }

    /**
     * Structured output with a single field.
     *
     * @param country country name
     * @return the capital city
     */
    @GetMapping("/capital")
    public GetCapitalResponse getCapital(@RequestParam String country) {
        return assistant.capital(country);
    }

    /**
     * Structured output with a richer typed record.
     *
     * @param country country name
     * @return typed facts about the capital
     */
    @GetMapping("/capital/details")
    public CapitalDetails getCapitalDetails(@RequestParam String country) {
        return assistant.capitalDetails(country);
    }

    /**
     * Structured output as a list.
     *
     * @param region geographic region
     * @return capital details for every country in the region
     */
    @GetMapping("/capitals")
    public List<CapitalDetails> getCapitals(@RequestParam String region) {
        return assistant.capitals(region);
    }
}
