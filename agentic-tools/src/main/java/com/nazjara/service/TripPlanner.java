package com.nazjara.service;

import com.nazjara.model.TripPlan;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.memory.ChatMemoryAccess;

/**
 * Builds a structured, validated trip plan.
 *
 * <p>The return type makes the proxy append JSON format instructions for {@link TripPlan} to the
 * user message (the starter's model has no native JSON-schema capability). After the tool loop,
 * the final reply goes through the output guardrail ({@code TripPlanGuardrail}) <em>before</em> it
 * is parsed. If the guardrail returns {@code reprompt(...)}, the proxy reads the conversation from
 * chat memory, appends the reprompt text as a new user message and calls the model again, running
 * any tool calls first, until the guardrail passes or {@code maxRetries} is used up
 * ({@code OutputGuardrailException}).
 *
 * <p>The memory id exists only for that retry: a reprompt is built from chat memory, and without
 * memory the model would receive nothing but the reprompt sentence. The caller passes a fresh id
 * per request and evicts it afterwards through {@link ChatMemoryAccess#evictChatMemory(Object)}.
 * Spring AI's counterpart is {@code StructuredOutputValidationAdvisor}, which validates the reply
 * against the JSON schema and re-asks with the errors.
 */
public interface TripPlanner extends ChatMemoryAccess {

    /**
     * Gathers forecast, flights, hotels and costs with the tools and returns a validated plan.
     *
     * @param planId throwaway memory id for this request
     * @param request trip request, e.g. "3 days in Lisbon from 2026-10-20, flying from Kyiv"
     * @return the plan that passed the guardrail
     */
    @SystemMessage(fromResource = "/prompts/planner-system.txt")
    @UserMessage("{{request}}")
    TripPlan plan(@MemoryId String planId, @V("request") String request);
}
