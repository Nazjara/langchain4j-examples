package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nazjara.model.Traveller;
import com.nazjara.model.TripRequest;
import com.nazjara.service.TripPlanner;
import java.util.List;
import dev.langchain4j.model.input.structured.StructuredPromptProcessor;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;

/**
 * Key-free checks of {@link TripRequest}'s {@code @StructuredPrompt} and of {@link TripPlanner}'s
 * {@code @UserMessage("{{it}}")}, against a test-local planner without it.
 */
class StructuredPromptTest {

    private static final TripRequest LISBON =
            new TripRequest("Lisbon", 3, List.of("museums", "food"), new Traveller("Ana", 34));

    private static final TripRequest WITH_BRACES =
            new TripRequest("Lisbon", 3, List.of("jazz {{live}}"), new Traveller("Ana", 34));

    interface OnlyArgumentPlanner {

        String plan(TripRequest request);
    }

    private final RecordingChatModel model = new RecordingChatModel();

    @Test
    void fieldsFillTheTemplate() {
        var prompt = StructuredPromptProcessor.toPrompt(LISBON);

        assertThat(prompt.text()).isEqualTo("""
                Plan a 3-day trip to Lisbon.
                Interests: [museums, food].
                Traveller: {name=Ana, age=34}.
                Give one short paragraph per day.""");
    }

    @Test
    void itTemplateSendsTheRenderedObjectOnce() {
        var planner = AiServices.create(TripPlanner.class, model);

        planner.plan(WITH_BRACES);

        assertThat(model.lastUserText()).isEqualTo(StructuredPromptProcessor.toPrompt(WITH_BRACES).text());
    }

    @Test
    void onlyArgumentFormRendersAgainAndFailsOnBraces() {
        var planner = AiServices.create(OnlyArgumentPlanner.class, model);

        assertThatThrownBy(() -> planner.plan(WITH_BRACES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Value for the variable 'live' is missing");
        assertThat(model.requests).isEmpty();
    }
}
