package com.nazjara.service;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * City guide whose system message and request limits are chosen per call.
 *
 * <p>There is no {@code @SystemMessage} here on purpose: the system message comes from the
 * provider set in {@code AiServicesConfig}, which LangChain4j calls only when the method and the
 * interface have none. Built with {@code AiServices.builder(..)}, because {@code @AiService} can't set
 * a system message provider or a request transformer.
 */
public interface CityGuide {

    /**
     * Answers a question about a city.
     *
     * @param userId chat memory key; ids starting with {@code guest} get shorter answers
     * @param question the question, inserted into the template once
     * @param parameters per-call values for the system message provider ({@code audience}); never sent to the model
     * @return Claude's answer
     */
    @UserMessage("{{question}}")
    String ask(@MemoryId String userId, @V("question") String question, InvocationParameters parameters);
}
