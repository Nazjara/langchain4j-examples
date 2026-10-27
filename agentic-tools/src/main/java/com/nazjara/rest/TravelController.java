package com.nazjara.rest;

import com.nazjara.model.AskResponse;
import com.nazjara.model.Question;
import com.nazjara.model.Role;
import com.nazjara.model.RoleAnswer;
import com.nazjara.model.ToolCall;
import com.nazjara.model.ToolSearch;
import com.nazjara.model.TripPlan;
import com.nazjara.service.StreamingTravelAssistant;
import com.nazjara.service.TravelAssistant;
import com.nazjara.service.TripPlanner;
import com.nazjara.tool.RoleToolProvider;
import com.nazjara.tool.TravelTools;
import java.util.Map;
import java.util.UUID;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.anthropic.AnthropicTokenUsage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * REST entry points for the travel agent. One HTTP request runs a whole tool loop inside the AI
 * Service proxy, so it may cause several model calls.
 */
@RestController
public class TravelController {

    private final TravelAssistant allToolsAssistant;
    private final TravelAssistant simpleSearchAssistant;
    private final TravelAssistant vectorSearchAssistant;
    private final TravelAssistant restrictedAssistant;
    private final StreamingTravelAssistant streamingAssistant;
    private final TripPlanner tripPlanner;

    /**
     * Creates the controller over the travel assistants (one per tool search mode, plus the
     * role-aware blocking and streaming ones) and the trip planner.
     *
     * @param allToolsAssistant assistant that sends all 15 tools with every request
     * @param simpleSearchAssistant assistant with {@code SimpleToolSearchStrategy}
     * @param vectorSearchAssistant assistant with {@code VectorToolSearchStrategy}
     * @param restrictedAssistant assistant whose tools come from the {@code RoleToolProvider}
     * @param streamingAssistant streaming assistant with the same provider
     * @param tripPlanner trip planner with the output guardrail
     */
    public TravelController(@Qualifier("allToolsAssistant") TravelAssistant allToolsAssistant,
                            @Qualifier("simpleSearchAssistant") TravelAssistant simpleSearchAssistant,
                            @Qualifier("vectorSearchAssistant") TravelAssistant vectorSearchAssistant,
                            @Qualifier("restrictedAssistant") TravelAssistant restrictedAssistant,
                            StreamingTravelAssistant streamingAssistant,
                            TripPlanner tripPlanner) {
        this.allToolsAssistant = allToolsAssistant;
        this.simpleSearchAssistant = simpleSearchAssistant;
        this.vectorSearchAssistant = vectorSearchAssistant;
        this.restrictedAssistant = restrictedAssistant;
        this.streamingAssistant = streamingAssistant;
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
     * Same as {@link #ask} without tool search, but the tools come from a {@code ToolProvider}
     * that depends on the caller's role: a {@code GUEST} is never offered the booking tools.
     * A call to a tool the role was not offered is answered with "not available" as a tool result,
     * so the model replies instead of the request failing.
     *
     * <p>Demo shortcut: the header is chosen by the caller, so anyone can claim {@code MEMBER} or another user's id. A real application takes both from the authenticated principal (e.g. Spring Security) and never from the request.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @param userId current user, as for {@link #ask}
     * @param role {@code GUEST} (default) or {@code MEMBER}
     * @return the answer and the tool calls behind it
     */
    @PostMapping("/ask/restricted")
    public RoleAnswer askRestricted(@RequestBody Question question,
                                    @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
                                    @RequestHeader(value = "X-Role", defaultValue = "GUEST") Role role) {
        var result = restrictedAssistant.ask(question.question(), roleParameters(userId, role));
        var toolCalls = result.toolExecutions().stream()
                .map(execution -> new ToolCall(execution.request().name(), execution.request().arguments(),
                        execution.result(), execution.hasFailed()))
                .toList();
        return new RoleAnswer(result.content(), role, result.intermediateResponses().size() + 1, toolCalls);
    }

    /**
     * Streaming variant of {@link #askRestricted}: server-sent events {@code text}, {@code tool-start},
     * {@code tool-end}, then {@code done} or {@code error}. Closing the connection cancels the
     * model call that is streaming.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @param userId current user, as for {@link #ask}
     * @param role {@code GUEST} (default) or {@code MEMBER}
     * @return the event stream
     */
    @PostMapping(path = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody Question question,
                                @RequestHeader(value = "X-User-Id", defaultValue = "demo-user") String userId,
                                @RequestHeader(value = "X-Role", defaultValue = "GUEST") Role role) {
        var emitter = new SseEmitter();
        TravelStreamEvents.attach(streamingAssistant.ask(question.question(), roleParameters(userId, role)), emitter);
        return emitter;
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

    private static InvocationParameters roleParameters(String userId, Role role) {
        return InvocationParameters.from(Map.of(TravelTools.USER_ID, userId, RoleToolProvider.ROLE, role));
    }

    private TravelAssistant assistant(ToolSearch toolSearch) {
        return switch (toolSearch) {
            case NONE -> allToolsAssistant;
            case SIMPLE -> simpleSearchAssistant;
            case VECTOR -> vectorSearchAssistant;
        };
    }
}
