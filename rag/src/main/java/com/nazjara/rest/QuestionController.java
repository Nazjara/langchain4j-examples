package com.nazjara.rest;

import static com.nazjara.bootstrap.DocumentIngester.CATEGORY_METADATA_KEY;
import static com.nazjara.bootstrap.DocumentIngester.SOURCE_METADATA_KEY;

import com.nazjara.model.Answer;
import com.nazjara.model.Question;
import com.nazjara.model.Source;
import com.nazjara.service.RagAssistant;
import com.nazjara.service.RoutingRagAssistant;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for the RAG example: one endpoint per query router. Both return the chunks that
 * reached Claude, tagged with their source category, so you can see which sources were searched.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final RagAssistant ragAssistant;
    private final RoutingRagAssistant routingRagAssistant;

    /**
     * Answers a question from every source (trucks and boats), each searched for its own top chunks.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the grounded answer and the chunks used
     */
    @PostMapping("/ask/augmented")
    public Answer askAugmented(@RequestBody Question question) {
        return toAnswer(ragAssistant.ask(question.question()));
    }

    /**
     * Answers a question from the sources Claude picks for it (one extra short Claude call).
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the grounded answer and the chunks used
     */
    @PostMapping("/ask/routed")
    public Answer askRouted(@RequestBody Question question) {
        return toAnswer(routingRagAssistant.ask(question.question()));
    }

    private static Answer toAnswer(Result<String> result) {
        var sources = result.sources().stream()
                .map(Content::textSegment)
                .map(segment -> new Source(segment.metadata().getString(CATEGORY_METADATA_KEY),
                        segment.metadata().getString(SOURCE_METADATA_KEY)))
                .toList();
        return new Answer(result.content(), sources);
    }
}
