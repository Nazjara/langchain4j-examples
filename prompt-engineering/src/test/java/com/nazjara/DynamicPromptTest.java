package com.nazjara;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.configuration.AiServicesConfig;
import com.nazjara.model.Audience;
import com.nazjara.service.CityGuide;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.junit.jupiter.api.Test;

/**
 * Key-free checks of the {@link CityGuide} wiring in {@link AiServicesConfig}: the system message
 * provider and the request transformer, over {@link RecordingChatModel}.
 */
class DynamicPromptTest {

    interface VisaDesk {

        @dev.langchain4j.service.SystemMessage("You answer visa questions only.")
        @UserMessage("{{it}}")
        String ask(String question);
    }

    private final RecordingChatModel model = new RecordingChatModel();
    private final CityGuide guide = new AiServicesConfig().cityGuide(model);

    @Test
    void systemMessageFollowsTheAudience() {
        guide.ask("alice", "What is there to see in Lisbon?", InvocationParameters.from("audience", Audience.KIDS));
        guide.ask("bob", "What is there to see in Lisbon?", InvocationParameters.from("audience", Audience.ADULTS));

        assertThat(systemText(0)).startsWith("You are a city guide for children under ten.");
        assertThat(systemText(1)).startsWith("You are a city guide for adults.");
    }

    @Test
    void annotatedSystemMessageWinsOverTheProvider() {
        var desk = AiServices.builder(VisaDesk.class)
                .chatModel(model)
                .systemMessageProviderWithContext(context -> "You are a city guide.")
                .build();

        desk.ask("Do I need a visa for Portugal?");

        assertThat(systemText(0)).isEqualTo("You answer visa questions only.");
    }

    @Test
    void transformerCapsTheAnswerLengthForGuests() {
        guide.ask("guest-17", "Suggest a dinner place.", InvocationParameters.from("audience", Audience.ADULTS));
        guide.ask("alice", "Suggest a dinner place.", InvocationParameters.from("audience", Audience.ADULTS));

        assertThat(model.requests.get(0).maxOutputTokens()).isEqualTo(200);
        assertThat(model.requests.get(1).maxOutputTokens()).isNull();
    }

    private String systemText(int requestIndex) {
        return ((SystemMessage) model.requests.get(requestIndex).messages().getFirst()).text();
    }
}
