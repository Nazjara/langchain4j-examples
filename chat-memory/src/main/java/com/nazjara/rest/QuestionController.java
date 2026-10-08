package com.nazjara.rest;

import com.nazjara.model.Answer;
import com.nazjara.model.Chunk;
import com.nazjara.model.HistoryEntry;
import com.nazjara.model.Question;
import com.nazjara.service.ChatAssistant;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST entry points for one conversation, identified by the {@code conversationId} path segment.
 *
 * <p>Each turn is its own request; the conversation id links the turns through the stored memory.
 * History and clearing go to the {@link ChatMemoryStore} directly, not through the AI Service's
 * {@code ChatMemoryAccess}: in LangChain4j 1.20.2 its {@code evictChatMemory(id)} only drops the
 * proxy's cached {@code ChatMemory} without clearing the store, and {@code getChatMemory(id)}
 * returns {@code null} for a conversation not used since startup.
 */
@RestController
@RequestMapping("/chat/{conversationId}")
@RequiredArgsConstructor
public class QuestionController {

    private final ChatAssistant chatAssistant;
    private final ChatMemoryStore chatMemoryStore;

    /**
     * Answers within the conversation and returns the whole reply at once.
     *
     * @param conversationId the conversation id
     * @param question JSON body {@code {"question": "..."}}
     * @return the model's reply
     */
    @PostMapping
    public Answer chat(@PathVariable String conversationId, @RequestBody Question question) {
        return new Answer(chatAssistant.chat(conversationId, question.question()));
    }

    /**
     * Answers within the conversation and streams the reply as Server-Sent Events.
     *
     * <p>Spring MVC subscribes to the {@link Flux} and writes each element as one {@code data:}
     * event. Elements are JSON objects rather than raw strings because an SSE client strips one
     * leading space after {@code data:}, which would glue words together. Closing the connection
     * cancels the {@link Flux}, but not the Anthropic call behind it (see
     * {@link ChatAssistant#chatStream(String, String)}).
     *
     * @param conversationId the conversation id
     * @param question JSON body {@code {"question": "..."}}
     * @return the reply as {@code {"text": "..."}} fragments
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Chunk> chatStream(@PathVariable String conversationId, @RequestBody Question question) {
        return chatAssistant.chatStream(conversationId, question.question()).map(Chunk::new);
    }

    /**
     * Returns the messages currently stored for the conversation: the window the model sees on the
     * next turn, including the system message.
     *
     * @param conversationId the conversation id
     * @return the stored messages, oldest first
     */
    @GetMapping
    public List<HistoryEntry> history(@PathVariable String conversationId) {
        return chatMemoryStore.getMessages(conversationId).stream()
                .map(HistoryEntry::from)
                .toList();
    }

    /**
     * Deletes the conversation's stored messages. The proxy may still cache a {@code ChatMemory}
     * for this id, but that memory reads the store on every turn, so the next turn starts empty.
     *
     * @param conversationId the conversation id
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String conversationId) {
        chatMemoryStore.deleteMessages(conversationId);
    }
}
