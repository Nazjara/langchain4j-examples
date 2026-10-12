package com.nazjara.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.model.Question;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.model.audio.TextToSpeechModel;
import dev.langchain4j.model.audio.TextToSpeechRequest;
import dev.langchain4j.model.audio.TextToSpeechResponse;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * The endpoint against a stub {@link TextToSpeechModel}, without Spring and without an API key.
 */
class QuestionControllerTest {

    private static final byte[] AUDIO_BYTES = {'I', 'D', '3', 4, 0};

    @Test
    void synthesizeSendsTextWithoutVoiceAndReturnsAudioBytes() {
        var requests = new ArrayList<TextToSpeechRequest>();
        TextToSpeechModel textToSpeechModel = request -> {
            requests.add(request);
            return TextToSpeechResponse.from(Audio.builder().binaryData(AUDIO_BYTES).mimeType("audio/mpeg").build());
        };
        var controller = new QuestionController(textToSpeechModel);

        var audio = controller.synthesize(new Question("Hello"));

        assertThat(audio).isEqualTo(AUDIO_BYTES);
        assertThat(requests).singleElement().satisfies(request -> {
            assertThat(request.text()).isEqualTo("Hello");
            assertThat(request.voice()).isNull();
        });
    }
}
