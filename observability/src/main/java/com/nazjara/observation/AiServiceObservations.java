package com.nazjara.observation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.AiServiceCompletedEvent;
import dev.langchain4j.observability.api.event.AiServiceErrorEvent;
import dev.langchain4j.observability.api.event.AiServiceStartedEvent;
import dev.langchain4j.observability.api.listener.AiServiceCompletedListener;
import dev.langchain4j.observability.api.listener.AiServiceErrorListener;
import dev.langchain4j.observability.api.listener.AiServiceListener;
import dev.langchain4j.observability.api.listener.AiServiceStartedListener;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;

/**
 * Turns an AI Service call into a parent span and each tool call into a child span, using two
 * LangChain4j hooks that have no ready-made Micrometer implementation:
 * <ul>
 *   <li><b>AI Service events</b>, registered with {@code AiServices.registerListeners(...)}. The
 *       proxy fires {@link AiServiceStartedEvent} before retrieval and the first model call, then
 *       exactly one of {@link AiServiceCompletedEvent} (carrying the method's return value) or
 *       {@link AiServiceErrorEvent} (from the proxy's outer {@code catch}). Every event carries the
 *       {@code InvocationContext}, whose {@code invocationId} pairs the end with its start. Other
 *       events exist (request issued, response received, tool executed, guardrail executed), but
 *       they fire after the fact, so they can annotate a span and cannot time one.</li>
 *   <li><b>Tool hooks</b> {@code AiServices.beforeToolExecution(...)} /
 *       {@code afterToolExecution(...)}. The blocking path calls them on the caller's thread around
 *       each tool; {@code afterToolExecution} also fires for a tool that failed and was turned
 *       into an error result ({@code hasFailed()}).</li>
 * </ul>
 * Because the AI Service observation stays open (its scope current) for the whole call, the
 * {@code chat} spans of {@code ObservationChatModelListener}, the {@code retrieve} span and the
 * tool spans all become its children:
 * <pre>
 * http post /ask
 * └─ ai_service CafeAssistant.ask
 *    ├─ retrieve
 *    ├─ chat claude-sonnet-5-5
 *    ├─ execute_tool getTodaysSpecial
 *    └─ chat claude-sonnet-5-5
 * </pre>
 * Tool spans follow the OpenTelemetry GenAI conventions for tool execution (span name
 * {@code execute_tool <name>}, {@code gen_ai.tool.name}, {@code gen_ai.tool.call.id}), which is also
 * what Spring AI emits. The tool name and {@code outcome} are low-cardinality, so they tag the
 * {@code langchain4j.tool} timer metric; everything else is high-cardinality (span only).
 *
 * <p>If a tool's exception escapes the tool loop (an error handler that rethrows), the
 * {@code after} hook does not run. The end of the AI Service call then closes that tool span as
 * failed, so no scope is left on the thread.
 *
 * <p><b>Limits.</b> Blocking AI Services only: streaming and async methods fire these hooks on
 * other threads, where the thread-local scopes would not nest. {@code executeToolsConcurrently()}
 * moves tools onto an executor for the same reason.
 *
 * <p>Spring AI needs none of this: {@code ChatClient}, advisors and tool calls are observed by the
 * framework.
 */
@RequiredArgsConstructor
public class AiServiceObservations {

    /** Observation name of the AI Service span and its timer metric. */
    public static final String AI_SERVICE = "langchain4j.ai_service";
    /** Observation name of the tool spans and their timer metric. */
    public static final String TOOL = "langchain4j.tool";

    private final ObservationRegistry observationRegistry;
    private final boolean includeContent;
    private final Map<UUID, OpenObservation> invocations = new ConcurrentHashMap<>();
    private final Map<ToolExecutionRequest, OpenTool> tools = new ConcurrentHashMap<>();

    /**
     * The AI Service listeners to pass to {@code AiServices.registerListeners(...)}.
     *
     * @return listeners for the started, completed and error events
     */
    public List<AiServiceListener<?>> listeners() {
        return List.of(
                (AiServiceStartedListener) this::started,
                (AiServiceCompletedListener) this::completed,
                (AiServiceErrorListener) this::failed);
    }

    /**
     * Starts the {@code execute_tool} observation as a child of the AI Service observation. Pass
     * it to {@code AiServices.beforeToolExecution(...)}.
     *
     * @param before the tool request and the invocation context
     */
    public void beforeTool(BeforeToolExecution before) {
        var request = before.request();
        var observation = Observation.createNotStarted(TOOL, observationRegistry)
                .contextualName("execute_tool " + request.name())
                .lowCardinalityKeyValue("gen_ai.operation.name", "execute_tool")
                .lowCardinalityKeyValue("gen_ai.tool.name", request.name())
                .highCardinalityKeyValue("gen_ai.tool.call.id", Objects.toString(request.id(), ""));
        if (includeContent) {
            observation.highCardinalityKeyValue("gen_ai.tool.call.arguments", Objects.toString(request.arguments(), ""));
        }
        tools.put(request, new OpenTool(invocationId(before.invocationContext()), OpenObservation.start(observation)));
    }

    /**
     * Stops the tool's observation, as failed when the tool returned an error result. Pass it to
     * {@code AiServices.afterToolExecution(...)}.
     *
     * @param execution the tool request, its result and timing
     */
    public void afterTool(ToolExecution execution) {
        var tool = tools.remove(execution.request());
        if (tool == null) {
            return;
        }
        if (includeContent) {
            tool.open().observation().highCardinalityKeyValue("gen_ai.tool.call.result", Objects.toString(execution.result(), ""));
        }
        if (execution.hasFailed()) {
            tool.open().fail(null);
        } else {
            tool.open().succeed();
        }
    }

    private void started(AiServiceStartedEvent event) {
        var context = event.invocationContext();
        var service = context.interfaceName().substring(context.interfaceName().lastIndexOf('.') + 1);
        var observation = Observation.createNotStarted(AI_SERVICE, observationRegistry)
                .contextualName("ai_service " + service + "." + context.methodName())
                .lowCardinalityKeyValue("langchain4j.ai_service.name", service)
                .lowCardinalityKeyValue("langchain4j.ai_service.method", context.methodName())
                .highCardinalityKeyValue("langchain4j.invocation.id", context.invocationId().toString());
        if (includeContent) {
            observation.highCardinalityKeyValue("langchain4j.user_message", text(event.userMessage()));
        }
        invocations.put(context.invocationId(), OpenObservation.start(observation));
    }

    private void completed(AiServiceCompletedEvent event) {
        var invocationId = event.invocationContext().invocationId();
        var open = invocations.remove(invocationId);
        if (open == null) {
            return;
        }
        if (includeContent) {
            var answer = event.result().map(result -> result instanceof Result<?> r ? r.content() : result);
            open.observation().highCardinalityKeyValue("langchain4j.answer", answer.map(String::valueOf).orElse(""));
        }
        failUnfinishedTools(invocationId);
        open.succeed();
    }

    private void failed(AiServiceErrorEvent event) {
        var invocationId = event.invocationContext().invocationId();
        var open = invocations.remove(invocationId);
        if (open == null) {
            return;
        }
        failUnfinishedTools(invocationId);
        open.fail(event.error());
    }

    private void failUnfinishedTools(UUID invocationId) {
        tools.entrySet().removeIf(entry -> {
            if (!invocationId.equals(entry.getValue().invocationId())) {
                return false;
            }
            entry.getValue().open().fail(null);
            return true;
        });
    }

    private static UUID invocationId(InvocationContext context) {
        return context == null ? null : context.invocationId();
    }

    private static String text(UserMessage message) {
        return message.hasSingleText() ? message.singleText() : message.contents().toString();
    }

    private record OpenTool(UUID invocationId, OpenObservation open) {
    }
}
