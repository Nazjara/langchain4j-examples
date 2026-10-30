package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.Audience;
import com.nazjara.model.Question;
import com.nazjara.model.TripRequest;
import com.nazjara.service.CityGuide;
import com.nazjara.service.TravelAssistant;
import com.nazjara.service.TripPlanner;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the Stage B prompt features. Each endpoint is one AI Service call; what
 * shapes or blocks the request happens inside the proxy, not here.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final TravelAssistant travelAssistant;
    private final TripPlanner tripPlanner;
    private final CityGuide cityGuide;

    /**
     * Travel question behind the input guardrails: injection attempts are rejected before Claude is
     * called, card numbers are masked before Claude and memory see them.
     *
     * @param conversationId chat memory key ({@code X-Conversation-Id}, default {@code default})
     * @param question JSON body {@code {"question": "..."}}
     * @return Claude's answer
     */
    @PostMapping("/ask")
    public Answer ask(@RequestHeader(name = "X-Conversation-Id", defaultValue = "default") String conversationId,
            @RequestBody Question question) {
        return new Answer(travelAssistant.ask(conversationId, question.question()));
    }

    /**
     * Trip plan from a {@code @StructuredPrompt} request body.
     *
     * @param request city, days, interests and traveller, rendered by the record's template
     * @return Claude's day-by-day plan
     */
    @PostMapping("/trip-plan")
    public Answer planTrip(@RequestBody TripRequest request) {
        return new Answer(tripPlanner.plan(request));
    }

    /**
     * City guide with a system message chosen per request and shorter answers for guests.
     *
     * @param userId chat memory key ({@code X-User-Id}, default {@code guest}); ids starting with
     *               {@code guest} are capped by the request transformer
     * @param audience {@code KIDS} or {@code ADULTS} ({@code X-Audience}, default {@code ADULTS}); passed
     *                 to the system message provider as an {@link InvocationParameters} value
     * @param question JSON body {@code {"question": "..."}}
     * @return Claude's answer
     */
    @PostMapping("/guide")
    public Answer guide(@RequestHeader(name = "X-User-Id", defaultValue = "guest") String userId,
            @RequestHeader(name = "X-Audience", defaultValue = "ADULTS") Audience audience,
            @RequestBody Question question) {
        var parameters = InvocationParameters.from("audience", audience);
        return new Answer(cityGuide.ask(userId, question.question(), parameters));
    }

    /**
     * Turns a rejected question into HTTP 400. No model call was made.
     *
     * @param exception the guardrail rejection
     * @return problem details with the guardrail's message
     */
    @ExceptionHandler(InputGuardrailException.class)
    public ProblemDetail handleRejectedQuestion(InputGuardrailException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Question rejected by an input guardrail");
        return problem;
    }
}
