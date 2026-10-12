package com.nazjara.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ImageContent;
import java.io.IOException;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class VisionAssistantTest {

    @Autowired
    VisionAssistant visionAssistant;

    @Test
    void describeNamesTheShapesInTheSampleImage() throws IOException {
        var bytes = new ClassPathResource("sample.png").getContentAsByteArray();
        var image = ImageContent.from(Base64.getEncoder().encodeToString(bytes), "image/png");

        var description = visionAssistant.describe(image);

        assertThat(description).containsIgnoringCase("circle").containsIgnoringCase("triangle");
    }
}
