package com.nazjara.rest;

import com.nazjara.model.Question;
import dev.langchain4j.model.audio.TextToSpeechModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for text-to-speech.
 *
 * <p>{@link TextToSpeechModel} is a sibling of {@code ChatModel}: it has no messages, tools or
 * memory, and AI Services cannot wrap it, so it is injected and called directly.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final TextToSpeechModel textToSpeechModel;

    /**
     * Speaks the given text.
     *
     * <p>{@link TextToSpeechModel#synthesize(String)} wraps the text in a
     * {@code TextToSpeechRequest} that names no voice, so the bean's default voice is used. OpenAI
     * answers with the audio itself as the HTTP body, not with JSON, which is why the result
     * exposes raw bytes and no base64 decoding is needed. Spring AI's counterpart is
     * {@code TextToSpeechModel.call(new TextToSpeechPrompt(text, options))}.
     *
     * @param question JSON body {@code {"question": "<text to speak>"}}, at most 4096 characters
     * @return MP3 audio
     */
    @PostMapping(path = "/audio", produces = "audio/mpeg")
    public byte[] synthesize(@RequestBody Question question) {
        return textToSpeechModel.synthesize(question.question()).audio().binaryData();
    }
}
