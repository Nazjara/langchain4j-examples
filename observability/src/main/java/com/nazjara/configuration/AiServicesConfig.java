package com.nazjara.configuration;

import com.nazjara.observation.AiServiceObservations;
import com.nazjara.service.CafeAssistant;
import com.nazjara.tool.CafeTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds {@link CafeAssistant} with {@link AiServices#builder(Class)}: {@code @AiService} has
 * attributes for the model, retriever and tools, but none for AI Service listeners or the
 * before/after tool hooks, which is where {@link AiServiceObservations} plugs in.
 *
 * <p>The {@code ChatModel} needs nothing extra: the starter already put
 * {@code ObservationChatModelListener} into it. The retriever already carries its listener too.
 */
@Configuration
public class AiServicesConfig {

    @Bean
    CafeAssistant cafeAssistant(ChatModel chatModel, ContentRetriever contentRetriever, CafeTools cafeTools,
                                AiServiceObservations observations) {
        return AiServices.builder(CafeAssistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .tools(cafeTools)
                .beforeToolExecution(observations::beforeTool)
                .afterToolExecution(observations::afterTool)
                .registerListeners(observations.listeners())
                .build();
    }
}
