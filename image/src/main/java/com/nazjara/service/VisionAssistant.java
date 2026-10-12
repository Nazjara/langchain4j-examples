package com.nazjara.service;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * Describes images with Claude.
 *
 * <p>A LangChain4j {@code UserMessage} holds a list of {@code Content} parts. Every earlier module
 * sent a single {@code TextContent}; here the message carries two parts. The generated proxy
 * builds it in two steps:
 * <ol>
 *   <li>The method-level {@link UserMessage} template is rendered into a {@code TextContent}.</li>
 *   <li>Every parameter annotated with {@link UserMessage} whose value is a {@code Content} (or a
 *       {@code List<Content>}) is appended after that text, so the model receives one user message
 *       with the text first and the image second.</li>
 * </ol>
 * {@code AnthropicChatModel} then maps the {@link ImageContent} to an {@code image} block of the
 * Messages API: a {@code base64} source when the image holds data, a {@code url} source when it
 * holds a link.
 *
 * <p>In Spring AI the image is a {@code Media} object attached to a hand-built
 * {@code UserMessage}, which is passed to {@code ChatClient} with {@code .messages(...)}.
 */
@AiService
public interface VisionAssistant {

    /**
     * Asks Claude to describe an image.
     *
     * @param image the image, as base64 data with its MIME type or as a URL
     * @return the model's description
     */
    @UserMessage(fromResource = "/prompts/describe-image.txt")
    String describe(@UserMessage ImageContent image);
}
