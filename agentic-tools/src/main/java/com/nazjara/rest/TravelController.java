package com.nazjara.rest;

import com.nazjara.model.AskResponse;
import com.nazjara.model.Question;
import com.nazjara.model.ToolCall;
import com.nazjara.model.ToolSearch;
import com.nazjara.model.TripPlan;
import com.nazjara.service.TravelAssistant;
import com.nazjara.service.TripPlanner;
import com.nazjara.tool.TravelTools;
import java.util.UUID;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry points for the travel agent. One HTTP request runs a whole tool loop inside the AI
 * Service proxy, so it may cause several model calls.
 */
@RestController
public class TravelController {

    private final TravelAssistant allToolsAssistant;
    private final TravelAssistant simpleSearchAssistant;
    private final TravelAssistant vectorSearchAssistant;
    private final TripPlanner tripPlanner;

    /**
     * Creates the controller over the three travel assistants (one per tool search mode) and the
     * trip planner.
     *
     * @param allToolsAssistant assistant that sends all 15 tools with every request
     * @param simpleSearchAssistant assistant with {@code SimpleToolSearchStrategy}
     * @param vectorSearchAssistant assistant with {@code VectorToolSearchStrategy}
     * @param tripPlanner trip planner with the output guardrail
     */
    public TravelController(@Qualifier("allToolsAssistant") TravelAssistant allToolsAssistant,
                            @Qualifier("simpleSearchAssistant") TravelAssistant simpleSearchAssistant,
                            @Qualifier("vectorSearchAssistant") TravelAssistant vectorSearchAssistant,
                            TripPlanner tripPlanner) {
        this.allToolsAssistant = allToolsAssistant;
        this.simpleSearchAssistant = simpleSearchAssistant;
        this.vectorSearchAssistant = vectorSearchAssistant;
        this.tripPlanner = tripPlanner;
    }

    /**
     * Travel question answered with tools. Ask the same question with each {@code toolSearch} mode
     * and compare model calls, tool calls and tokens. With a search mode, the first tool call is
     * {@code tool_search_tool}, and its result lists the tools the model found.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @param userId current user; bookings are made for this id, which the model never sees
     * @param toolSearch {@code NONE} (all tools), {@code SIMPLE} or {@code VECTOR}
     * @return the answer, the tool calls behind it and the token usage summed over all model calls
     */
    @PostMapping("/ask")
    public AskResponse ask(@RequestBody Question question,
                           @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
                           @RequestParam(defaultValue = "NONE") ToolSearch toolSearch) {
        var result = assistant(toolSearch).ask(question.question(), InvocationParameters.from(TravelTools.USER_ID, userId));
        var toolCalls = result.toolExecutions().stream()
                .map(execution -> new ToolCall(execution.request().name(), execution.request().arguments(),
                        execution.result(), execution.hasFailed()))
                .toList();
        var usage = result.tokenUsage();
        var anthropicUsage = usage instanceof AnthropicTokenUsage anthropic ? anthropic : null;
        return new AskResponse(result.content(), toolSearch, result.intermediateResponses().size() + 1, toolCalls,
                usage.inputTokenCount(), usage.outputTokenCount(),
                anthropicUsage == null ? null : anthropicUsage.cacheReadInputTokens(),
                anthropicUsage == null ? null : anthropicUsage.cacheCreationInputTokens());
    }

    /**
     * Structured trip plan, checked by the output guardrail and reprompted when invalid. Each call
     * gets a fresh memory id, which is evicted afterwards.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the validated plan
     */
    @PostMapping("/plan")
    public TripPlan plan(@RequestBody Question question) {
        var planId = UUID.randomUUID().toString();
        try {
            return tripPlanner.plan(planId, question.question());
        } finally {
            tripPlanner.evictChatMemory(planId);
        }
    }

    /**
     * Turns a guardrail that still failed after its last reprompt into HTTP 422.
     *
     * @param exception the guardrail failure
     * @return problem details with the guardrail's messages
     */
    @ExceptionHandler(OutputGuardrailException.class)
    public ProblemDetail handleGuardrailFailure(OutputGuardrailException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle("Trip plan failed validation");
        return problem;
    }

    private TravelAssistant assistant(ToolSearch toolSearch) {
        return switch (toolSearch) {
            case NONE -> allToolsAssistant;
            case SIMPLE -> simpleSearchAssistant;
            case VECTOR -> vectorSearchAssistant;
        };
    }
}
