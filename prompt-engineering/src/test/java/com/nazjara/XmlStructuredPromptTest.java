package com.nazjara;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

/**
 * XML tags separate instructions from data, so text inside a document is never mistaken for
 * an instruction, and prompts can refer to parts by name. With long inputs, put the documents
 * first and the task last: answers get measurably better when the question follows the data.
 *
 * <p>The templates in {@code prompts/} keep that layout visible: {@code {{reviews}}} on top,
 * {@code <instructions>} at the bottom. The proxy fills the variable from the {@code @V}
 * argument. Spring AI builds the same text inline with {@code String.formatted(...)}.
 */
class XmlStructuredPromptTest extends BaseTestClass {

    interface ReviewWriter {

        @UserMessage(fromResource = "/prompts/product-description.txt")
        String productDescription(@V("reviews") String reviews);

        @UserMessage(fromResource = "/prompts/grounded-criticism.txt")
        String groundedCriticism(@V("reviews") String reviews);
    }

    private final ReviewWriter writer = AiServices.create(ReviewWriter.class, modelBuilder().build());

    @Test
    void documentsFirstTaskLast() {
        var answer = writer.productDescription(Reviews.asXml(Reviews.BOOK));

        System.out.println(answer);
    }

    @Test
    void quotesGroundTheAnswer() {
        var answer = writer.groundedCriticism(Reviews.asXml(Reviews.TUMBLER));

        System.out.println(answer);
    }
}
