package com.nazjara.rest;

import static org.springframework.http.MediaType.IMAGE_PNG_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;

import com.nazjara.model.Answer;
import com.nazjara.model.Question;
import com.nazjara.service.VisionAssistant;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.image.ImageModel;
import java.io.IOException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST entry points for image generation (OpenAI) and image understanding (Claude).
 *
 * <p>The two endpoints use different kinds of model. {@link ImageModel} is a sibling of
 * {@code ChatModel}, not a chat model: it has no messages, tools or memory, and AI Services cannot
 * wrap it, so it is injected and called directly. Vision is an ordinary chat call and goes through
 * the {@link VisionAssistant} AI Service.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final ImageModel imageModel;
    private final VisionAssistant visionAssistant;

    /**
     * Generates an image from a text prompt.
     *
     * <p>{@link ImageModel#generate(String)} takes only the prompt: model, size, quality and
     * output format are fixed on the bean, which the OpenAI starter builds from
     * {@code langchain4j.open-ai.image-model.*}. The image comes back as a base64 string inside
     * the JSON response and is decoded to bytes here. Spring AI's
     * {@code OpenAiImageModel.call(new ImagePrompt(text, options))} takes the same settings per
     * call.
     *
     * @param question JSON body {@code {"question": "<image description>"}}
     * @return the PNG image
     */
    @PostMapping(path = "/image", produces = IMAGE_PNG_VALUE)
    public byte[] generateImage(@RequestBody Question question) {
        var image = imageModel.generate(question.question()).content();
        return Base64.getDecoder().decode(image.base64Data());
    }

    /**
     * Describes an uploaded image.
     *
     * <p>The upload arrives as raw bytes in a multipart part. The Anthropic Messages API accepts
     * only JSON, so the bytes are base64-encoded into an {@link ImageContent} before the call.
     *
     * @param file multipart field {@code file}
     * @return the model's description
     * @throws IOException if the upload cannot be read
     */
    @PostMapping(path = "/vision", consumes = MULTIPART_FORM_DATA_VALUE)
    public Answer describeImage(@RequestParam MultipartFile file) throws IOException {
        var image = ImageContent.from(Base64.getEncoder().encodeToString(file.getBytes()), file.getContentType());
        return new Answer(visionAssistant.describe(image));
    }
}
