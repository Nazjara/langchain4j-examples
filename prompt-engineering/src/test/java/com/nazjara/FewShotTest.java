package com.nazjara;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Examples are the strongest signal in a prompt: the model copies their length, tone and shape.
 * Use them for what instructions describe poorly (a house style, an exact output shape), not for
 * tasks the model already does well. Pass them as user/assistant message pairs so they read as
 * prior turns, and vary them so the model learns the pattern rather than one sample.
 *
 * <p>This is the one test that skips the AI Service. Its annotations describe a system message
 * and a single user message, with no way to declare fake {@link AiMessage} turns in between, so
 * the message list goes straight to {@link ChatModel#chat(List)}. Spring AI keeps this inside the
 * fluent API with {@code chatClient.prompt().messages(...)}.
 */
class FewShotTest extends BaseTestClass {

    private static final List<ChatMessage> HOUSE_STYLE_EXAMPLES = List.of(
            UserMessage.from("Feature: double-wall vacuum insulation keeps drinks cold for 24 hours"),
            AiMessage.from("Ice at breakfast. Ice at bedtime."),
            UserMessage.from("Feature: laptop battery lasts 20 hours on a single charge"),
            AiMessage.from("Leave the charger. Take the long flight."),
            UserMessage.from("Feature: hiking boots are waterproof and weigh 400 grams per pair"),
            AiMessage.from("Puddles lose. Your legs win."));

    private final ChatModel model = modelBuilder().build();

    @Test
    void examplesPinTheHouseStyle() {
        var messages = new ArrayList<ChatMessage>();
        messages.add(SystemMessage.from("You write product taglines in the brand's house style."));
        messages.addAll(HOUSE_STYLE_EXAMPLES);
        messages.add(UserMessage.from("Feature: noise-cancelling headphones block 95% of ambient sound"));

        var answer = model.chat(messages).aiMessage().text();

        System.out.println(answer);
    }
}
