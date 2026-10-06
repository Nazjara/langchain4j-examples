package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.Question;
import com.nazjara.service.ManualRagService;
import com.nazjara.service.RagAssistant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST entry point for the RAG example: one endpoint per way of doing RAG in LangChain4j.
 */
@RestController
@RequiredArgsConstructor
public class QuestionController {

    private final ManualRagService manualRagService;
    private final RagAssistant ragAssistant;

    /**
     * Answers a question from the loaded documents with hand-written retrieval and templating.
     * Same endpoint and body as spring-ai-examples' {@code rag}.
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the grounded answer
     */
    @PostMapping("/ask")
    public Answer askQuestion(@RequestBody Question question) {
        return manualRagService.getAnswer(question);
    }

    /**
     * Answers the same question through the AI Service's retrieval augmentor (LangChain4j-only).
     *
     * @param question JSON body {@code {"question": "..."}}
     * @return the grounded answer
     */
    @PostMapping("/ask/augmented")
    public Answer askAugmented(@RequestBody Question question) {
        return new Answer(ragAssistant.ask(question.question()));
    }
}
