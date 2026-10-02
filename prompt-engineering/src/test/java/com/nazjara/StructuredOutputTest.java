package com.nazjara;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Output format belongs in a schema, not in prompt text. The AI Service derives a JSON schema
 * from the method's return type, and because the model declares
 * {@code RESPONSE_FORMAT_JSON_SCHEMA}, sends it as Anthropic's native {@code output_config.format}.
 * The response is then constrained to the schema while it is generated, instead of the format
 * being described in the prompt and parsed on a best-effort basis.
 *
 * <p>Spring AI needs {@code .entity(Type.class, spec -> spec.useProviderStructuredOutput())} for
 * the same request. Here the opt-in is a model capability, and the return type does the rest.
 */
class StructuredOutputTest extends BaseTestClass {

    private static final String ARTICLE = """
            In a recent survey conducted by the government, public sector employees were asked to \
            rate their level of satisfaction with the department they work at. NASA was the most \
            popular department with a satisfaction rating of 95%. The Social Security Administration \
            had the lowest rating, with only 45% of employees satisfied. The government has pledged \
            to address the concerns raised and work towards improving job satisfaction.""";

    enum Sentiment { POSITIVE, NEUTRAL, MIXED, NEGATIVE }

    record ReviewAnalysis(
            @Description("The review's index attribute") int index,
            Sentiment sentiment,
            @Description("Emotions the writer expresses") List<String> emotions,
            @Description("Whether the writer is angry") boolean angry,
            @Description("One-sentence summary in English") String summary) {
    }

    record ReviewAnalyses(List<ReviewAnalysis> reviews) {
    }

    record Topics(@Description("Topics of one or two words each") List<String> topics) {
    }

    interface Analyst {

        @UserMessage(fromResource = "/prompts/classify-reviews.txt")
        ReviewAnalyses classifyReviews(@V("reviews") String reviews);

        @UserMessage(fromResource = "/prompts/extract-topics.txt")
        Topics extractTopics(@V("article") String article);
    }

    private final Analyst analyst = AiServices.create(Analyst.class,
            modelBuilder().supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA)).build());

    @Test
    void classifyReviews() {
        var result = analyst.classifyReviews(Reviews.asXml(Reviews.TUMBLER));

        result.reviews().forEach(System.out::println);
        assertThat(result.reviews()).hasSize(Reviews.TUMBLER.size());
    }

    @Test
    void extractTopics() {
        var result = analyst.extractTopics(ARTICLE);

        System.out.println(result.topics());
        assertThat(result.topics()).isNotEmpty();
    }
}
