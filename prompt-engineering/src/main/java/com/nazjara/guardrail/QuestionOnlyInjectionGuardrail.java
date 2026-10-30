package com.nazjara.guardrail;

import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrails.PatternBasedPromptInjectionGuardrail;

/**
 * Prompt-injection check on the traveller's question only.
 *
 * <p>The ready-made {@link PatternBasedPromptInjectionGuardrail} ({@code langchain4j-guardrails},
 * about 20 OWASP LLM01 regexes such as "ignore previous instructions", "act as a", "reveal your
 * system prompt") checks the whole rendered user message. That includes the service's own template,
 * so a template starting "Act as a travel agent" would reject every request. This subclass reads the
 * {@code question} template variable from the request's parameters and runs the same patterns on
 * that text alone; on success the full rendered message goes on unchanged.
 *
 * <p>The patterns stay blunt: "Can you act as a tour guide?" is rejected, "Disregard what you were
 * told earlier" passes. The parent logs the matched input at WARN, so user text reaches the logs.
 */
public class QuestionOnlyInjectionGuardrail extends PatternBasedPromptInjectionGuardrail {

    /**
     * Checks the {@code question} variable against the injection patterns.
     *
     * @param request the rendered user message plus its template variables, memory and context
     * @return success, or a fatal result that makes the AI Service throw {@code InputGuardrailException}
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        var question = String.valueOf(request.requestParams().variables().get("question"));
        return super.validate(request.withText(question));
    }
}
