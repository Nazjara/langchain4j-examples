package com.nazjara.guardrail;

import com.nazjara.model.TripPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrails.JsonExtractorOutputGuardrail;

/**
 * Output guardrail for {@link TripPlan}, in two steps.
 *
 * <ol>
 *   <li><b>Syntax</b>, inherited from {@link JsonExtractorOutputGuardrail} ({@code langchain4j-guardrails}):
 *       extracts the JSON from the reply and parses it into {@link TripPlan}. On failure it returns
 *       {@code reprompt("Invalid JSON", "Make sure you return a valid JSON object following the
 *       specified format")}. Parsing alone accepts missing fields: Jackson leaves them {@code null}.</li>
 *   <li><b>Content</b>, added here: required fields and business rules that no JSON parser checks.
 *       Every problem found goes into one reprompt, so the model can fix them all in one retry.</li>
 * </ol>
 *
 * On success the parsed plan is returned through {@code successWith(json, plan)}, and the proxy
 * returns that object instead of parsing the reply a second time.
 *
 * <p>Spring AI's {@code StructuredOutputValidationAdvisor} validates against the generated JSON
 * schema, which covers required fields and types but not rules such as consecutive day numbers.
 */
public class TripPlanGuardrail extends JsonExtractorOutputGuardrail<TripPlan> {

    /**
     * Creates the guardrail for the {@link TripPlan} type.
     */
    public TripPlanGuardrail() {
        super(TripPlan.class);
    }

    /**
     * Parses the reply and checks the plan's content.
     *
     * @param responseFromLLM the final reply of the tool loop (or of a reprompt)
     * @return success carrying the parsed plan, or a reprompt listing every problem found
     */
    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        var parsed = super.validate(responseFromLLM);
        if (!parsed.isSuccess()) {
            return parsed;
        }
        var problems = problems((TripPlan) parsed.successfulResult());
        if (problems.isEmpty()) {
            return parsed;
        }
        var summary = String.join("; ", problems);
        return reprompt("Invalid trip plan: " + summary,
                "The trip plan is invalid: %s. Fix these problems and return the complete trip plan as JSON again."
                        .formatted(summary));
    }

    private static List<String> problems(TripPlan plan) {
        var problems = new ArrayList<String>();
        if (plan.destination() == null || plan.destination().isBlank()) {
            problems.add("destination is missing");
        }
        if (!isIsoDate(plan.startDate())) {
            problems.add("startDate must be an ISO-8601 date (yyyy-MM-dd)");
        }
        if (plan.days() == null || plan.days().isEmpty()) {
            problems.add("days is empty");
        } else {
            for (var index = 0; index < plan.days().size(); index++) {
                var day = plan.days().get(index);
                if (day.day() != index + 1) {
                    problems.add("days must be numbered 1..%d in order, found %d at position %d"
                            .formatted(plan.days().size(), day.day(), index + 1));
                }
                if (day.weather() == null || day.weather().isBlank()) {
                    problems.add("day %d has no weather".formatted(index + 1));
                }
                if (day.activities() == null || day.activities().isEmpty()) {
                    problems.add("day %d has no activities".formatted(index + 1));
                }
            }
        }
        if (plan.packingList() == null || plan.packingList().isEmpty()) {
            problems.add("packingList is empty");
        }
        if (plan.estimatedBudgetEur() == null || plan.estimatedBudgetEur().compareTo(BigDecimal.ZERO) <= 0) {
            problems.add("estimatedBudgetEur must be a positive amount");
        }
        return problems;
    }

    private static boolean isIsoDate(String value) {
        if (value == null) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
