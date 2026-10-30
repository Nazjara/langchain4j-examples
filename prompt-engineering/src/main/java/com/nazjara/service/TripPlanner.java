package com.nazjara.service;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

import com.nazjara.model.TripRequest;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * Trip planning from a {@code @StructuredPrompt} object.
 *
 * <p>{@link TripRequest} carries its template in the annotation. The proxy turns the object into a
 * map with Jackson and replaces each {@code {{field}}}: plain substitution, so the interests list
 * renders as {@code [a, b]} and the traveller as {@code {name=Ana, age=34}}.
 *
 * <p>{@code @UserMessage("{{it}}")} matters. Without it, the object as the only argument would itself
 * become the method's template and be rendered a second time, so a field containing {@code {{x}}}
 * would fail the call ("Value for the variable 'x' is missing") and {@code {{current_date}}} would be
 * replaced. With it, the rendered prompt is inserted once. Spring AI's counterpart is a
 * {@code PromptTemplate} with a parameter map; its StringTemplate engine also has loops and
 * conditionals, which LangChain4j templates lack.
 */
@AiService(wiringMode = EXPLICIT, chatModel = "anthropicChatModel")
public interface TripPlanner {

    /**
     * Plans a trip.
     *
     * @param request the structured prompt
     * @return Claude's day-by-day plan
     */
    @UserMessage("{{it}}")
    String plan(TripRequest request);
}
