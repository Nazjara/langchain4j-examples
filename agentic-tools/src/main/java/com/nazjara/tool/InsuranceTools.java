package com.nazjara.tool;

import com.nazjara.model.InsuranceQuote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Travel insurance tools that the model never sees directly. They are attached to the
 * {@code travel-insurance} skill ({@code SkillsConfig}), so they join the tool list only after the
 * model calls {@code activate_skill("travel-insurance")}. Until then a call to them has no executor
 * and goes to the hallucinated-tool-name strategy.
 *
 * <p>The tier details (what each tier covers) are not here but in the skill's {@code coverage.md}
 * resource, which the model reads through {@code read_skill_resource}. Prices are fake and
 * deterministic.
 */
@Slf4j
@Component
public class InsuranceTools {

    /**
     * Fake quotes for the three tiers.
     *
     * @param destination destination country
     * @param days trip length in days
     * @param travellerAge age of the traveller
     * @return one quote per tier
     */
    @Tool("Quote travel insurance for a trip: one quote per tier (BASIC, STANDARD, PREMIUM), prices in EUR")
    public List<InsuranceQuote> quoteInsurance(@P("Destination country") String destination,
                                               @P("Trip length in days") int days,
                                               @P("Age of the traveller in years") int travellerAge) {
        var ageFactor = travellerAge >= 65 ? new BigDecimal("1.8") : BigDecimal.ONE;
        var seed = TravelTools.seed(destination, String.valueOf(days), String.valueOf(travellerAge));
        return List.of(
                quote("BASIC", "1.20", days, ageFactor, seed),
                quote("STANDARD", "2.40", days, ageFactor, seed),
                quote("PREMIUM", "4.10", days, ageFactor, seed));
    }

    /**
     * Fake policy purchase for the signed-in user from {@link InvocationParameters}, like
     * {@code TravelTools.bookFlight}.
     *
     * @param quoteId quote to buy, from {@link #quoteInsurance}
     * @param parameters application-provided invocation parameters
     * @return policy confirmation
     */
    @Tool("Buy the travel insurance policy for a quote id. Always buys for the signed-in user; it cannot buy for anyone else")
    public String buyInsurance(@P("Quote id from quoteInsurance") String quoteId, InvocationParameters parameters) {
        var userId = TravelTools.currentUser(parameters);
        log.info("Buying insurance {} for user {}", quoteId, userId);
        return "Policy P-%d issued for user %s (quote %s)".formatted(TravelTools.seed(quoteId, userId), userId, quoteId);
    }

    private static InsuranceQuote quote(String tier, String eurPerDay, int days, BigDecimal ageFactor, int seed) {
        var price = new BigDecimal(eurPerDay).multiply(BigDecimal.valueOf(days)).multiply(ageFactor).setScale(2, RoundingMode.HALF_UP);
        return new InsuranceQuote("Q-%s-%d".formatted(tier, seed % 10000), tier, price);
    }
}
