package com.nazjara.rest;

import com.nazjara.model.AnswerWithSources;
import com.nazjara.model.Question;
import com.nazjara.model.Source;
import com.nazjara.service.CafeAssistant;
import dev.langchain4j.rag.content.ContentMetadata;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for the café assistant. Each request becomes one trace: Spring Boot opens the
 * {@code http post /ask} server span before this method runs, and every LangChain4j span of the
 * call nests under it.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final CafeAssistant cafeAssistant;
    private final Tracer tracer;

    /**
     * Answers a guest question.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the answer, the retrieved chunks with their similarity scores, and the trace id to
     * paste into Grafana's Tempo search. The same id is on this request's log lines.
     */
    @PostMapping("/ask")
    public AnswerWithSources ask(@RequestBody Question question) {
        var result = cafeAssistant.ask(question.question());
        var sources = result.sources().stream()
                .map(content -> new Source(content.textSegment().text(),
                        content.metadata().get(ContentMetadata.SCORE) instanceof Double score ? score : null))
                .toList();
        var span = tracer.currentSpan();
        return new AnswerWithSources(result.content(), sources, span == null ? null : span.context().traceId());
    }
}
