package com.nazjara.service;

import com.nazjara.model.CapitalDetails;
import com.nazjara.model.GetCapitalResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import java.util.List;

/**
 * Entry-level LangChain4j operations: a plain chat call and structured output.
 *
 * <p>There is no implementation class. At startup the LangChain4j Spring Boot starter finds
 * {@code @AiService} interfaces, calls {@code AiServices.builder(Assistant.class)} with the
 * {@code ChatModel} bean, and registers the generated JDK dynamic proxy as a bean. On each call
 * the proxy renders the prompt from the annotations and arguments, calls
 * {@code ChatModel.chat(ChatRequest)}, and converts the {@code ChatResponse} into the method's
 * return type. In Spring AI the same work is written by hand against {@code ChatClient}.
 *
 * <p>Structured output is driven only by the declared return type: the proxy reads it via
 * reflection (generics included, so {@code List<CapitalDetails>} needs no type token), builds a
 * JSON schema using the {@code @Description} texts of the record fields, and parses the reply
 * with Jackson.
 *
 * <p>Every method is a single, stateless request: nothing is remembered between calls.
 */
@AiService
public interface Assistant {

    /**
     * Sends the question as-is and returns the model's free-text reply. With no
     * {@code @UserMessage}, the single {@code String} argument becomes the user message.
     *
     * @param question the user's question
     * @return the model's answer as plain text
     */
    String ask(String question);

    /**
     * Same as {@link #ask(String)}, but wraps the answer in {@link Result}, which also carries
     * token usage, finish reason, and (in later modules) RAG sources and tool executions. The
     * Spring AI counterpart is {@code .call().chatResponse()}.
     *
     * @param question the user's question
     * @return the answer plus call metadata
     */
    Result<String> askWithMetadata(String question);

    /**
     * Asks for the capital of a country and maps the reply onto a one-field record. The
     * template's {@code {{country}}} is filled from the {@code @V("country")} argument.
     *
     * @param country country name, e.g. {@code "France"}
     * @return the capital city
     */
    @UserMessage(fromResource = "/prompts/get-capital.txt")
    GetCapitalResponse capital(@V("country") String country);

    /**
     * Asks for several facts about a country's capital and maps them onto a typed record;
     * numbers such as {@code population} come back already parsed.
     *
     * @param country country name
     * @return typed facts about the capital
     */
    @UserMessage(fromResource = "/prompts/get-capital-details.txt")
    CapitalDetails capitalDetails(@V("country") String country);

    /**
     * Asks for the capitals of all countries in a region and maps the reply onto a list.
     *
     * @param region a geographic region, e.g. {@code "Scandinavia"}
     * @return one entry per country in the region
     */
    @UserMessage(fromResource = "/prompts/get-capitals.txt")
    List<CapitalDetails> capitals(@V("region") String region);
}
