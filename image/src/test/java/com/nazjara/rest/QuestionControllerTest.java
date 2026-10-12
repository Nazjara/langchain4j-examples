package com.nazjara.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.model.Question;
import com.nazjara.service.VisionAssistant;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Both endpoints against stub models, without Spring and without API keys.
 *
 * <p>The vision test builds the real {@link VisionAssistant} proxy over a recording
 * {@link ChatModel}, so it shows the exact {@link UserMessage} LangChain4j assembles from the
 * template and the {@link ImageContent} parameter.
 */
class QuestionControllerTest {

    private static final byte[] IMAGE_BYTES = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
    private static final String IMAGE_BASE64 = Base64.getEncoder().encodeToString(IMAGE_BYTES);

    @Test
    void generateImageSendsPromptAndDecodesBase64Image() {
        var prompts = new ArrayList<String>();
        ImageModel imageModel = prompt -> {
            prompts.add(prompt);
            return Response.from(Image.builder().base64Data(IMAGE_BASE64).mimeType("image/png").build());
        };
        var controller = new QuestionController(imageModel, image -> "unused");

        var image = controller.generateImage(new Question("A boat"));

        assertThat(image).isEqualTo(IMAGE_BYTES);
        assertThat(prompts).containsExactly("A boat");
    }

    @Test
    void describeImageSendsTemplateTextThenImageInOneUserMessage() throws IOException {
        var chatModel = new RecordingChatModel("Three shapes.");
        var visionAssistant = AiServices.builder(VisionAssistant.class).chatModel(chatModel).build();
        var controller = new QuestionController(prompt -> Response.from(Image.builder().build()), visionAssistant);
        var file = new MockMultipartFile("file", "sample.png", "image/png", IMAGE_BYTES);

        var answer = controller.describeImage(file);

        assertThat(answer.answer()).isEqualTo("Three shapes.");
        assertThat(chatModel.requests).singleElement()
                .satisfies(request -> assertThat(request.messages()).singleElement()
                        .isInstanceOfSatisfying(UserMessage.class, message -> {
                            assertThat(message.contents()).hasSize(2);
                            assertThat(message.contents().getFirst())
                                    .isInstanceOfSatisfying(TextContent.class, text ->
                                            assertThat(text.text()).isEqualTo("Explain what you see in this picture."));
                            assertThat(message.contents().getLast())
                                    .isInstanceOfSatisfying(ImageContent.class, content -> {
                                        assertThat(content.image().base64Data()).isEqualTo(IMAGE_BASE64);
                                        assertThat(content.image().mimeType()).isEqualTo("image/png");
                                    });
                        }));
    }

    /**
     * Answers every request with the same text and records the requests.
     */
    private static final class RecordingChatModel implements ChatModel {

        private final String reply;
        private final List<ChatRequest> requests = new ArrayList<>();

        private RecordingChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
        }
    }
}
