package com.nazjara.service;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import com.nazjara.guardrail.CardNumberRedactor;
import com.nazjara.guardrail.QuestionOnlyInjectionGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.spring.AiService;

/**
 * Travel-agent chat guarded by two input guardrails.
 *
 * <p>{@code @AiService} has no guardrail attribute, but the starter builds the proxy with
 * {@code AiServices.builder(..)}, which reads {@link InputGuardrails} from the interface or method.
 * LangChain4j creates each listed class with its no-argument constructor (the starter registers no
 * factory), so guardrails declared this way can't receive Spring beans; a guardrail that needs one
 * goes on {@code AiServices.builder().inputGuardrails(..)} in a {@code @Configuration} instead, which
 * replaces the annotations for every method.
 *
 * <p>On each call the proxy renders {@code prompts/travel-agent.txt}, then passes the rendered user
 * message through {@link QuestionOnlyInjectionGuardrail} and {@link CardNumberRedactor} in order, then
 * writes memory and calls Claude. A rejection throws {@code InputGuardrailException} before the model
 * call, so no tokens are spent and nothing reaches memory. The guardrails run once per call: tool
 * results and later model rounds are never checked. Spring AI's counterpart is {@code SafeGuardAdvisor}
 * (a word list answered with a fixed reply) or a custom {@code CallAdvisor}.
 */
@AiService(wiringMode = EXPLICIT, chatModel = "anthropicChatModel", chatMemoryProvider = "conversationMemory")
@InputGuardrails({QuestionOnlyInjectionGuardrail.class, CardNumberRedactor.class})
public interface TravelAssistant {

    /**
     * Answers a travel question within a conversation.
     *
     * @param conversationId chat memory key
     * @param question the traveller's question, inserted into the template once
     * @return Claude's answer
     */
    @UserMessage(fromResource = "/prompts/travel-agent.txt")
    String ask(@MemoryId String conversationId, @V("question") String question);
}
