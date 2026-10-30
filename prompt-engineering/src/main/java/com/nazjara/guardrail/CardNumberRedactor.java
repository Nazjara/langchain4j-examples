package com.nazjara.guardrail;

import java.util.regex.Pattern;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;

/**
 * Replaces card numbers in the user message before it reaches the model.
 *
 * <p>{@code successWith(text)} passes the call with rewritten text; the next guardrail and the model
 * see only the rewrite. Whether chat memory keeps it depends on the AI Service's
 * {@code storeRetrievedContentInChatMemory}: with the default {@code true}, memory stores the guarded
 * message; with {@code false} it stores the message from before RAG and before the guardrails, so the
 * card number would be sent again on the next turn.
 */
public class CardNumberRedactor implements InputGuardrail {

    private static final Pattern CARD_NUMBER = Pattern.compile("\\b\\d{4}(?:[ -]?\\d{4}){3}\\b");

    /**
     * Masks every 16-digit card number.
     *
     * @param request the rendered user message
     * @return success unchanged, or success with the masked text
     */
    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        var text = request.userMessage().singleText();
        var redacted = CARD_NUMBER.matcher(text).replaceAll("[card removed]");
        return redacted.equals(text) ? success() : successWith(redacted);
    }
}
