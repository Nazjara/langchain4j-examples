package com.nazjara.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.audio.TextToSpeechModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class TextToSpeechModelTest {

    @Autowired
    TextToSpeechModel textToSpeechModel;

    @Test
    void synthesizeReturnsMp3Audio() {
        var audio = textToSpeechModel.synthesize("Hello from LangChain4j.").audio();

        assertThat(audio.mimeType()).isEqualTo("audio/mpeg");
        assertThat(audio.binaryData()).isNotEmpty();
    }
}
