package com.nazjara.configuration;

import com.nazjara.observation.AiServiceObservations;
import com.nazjara.observation.RetrievalObservationListener;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.observation.listener.ObservationChatModelListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The listeners that turn LangChain4j calls into Micrometer Observations. Spring Boot does the
 * rest: {@code spring-boot-starter-opentelemetry} registers handlers that turn every observation
 * into a timer metric (exported by the OTLP meter registry) and a span (Micrometer Tracing → OTel
 * SDK → OTLP). Boot's Docker Compose support recognises the {@code grafana/otel-lgtm} image and
 * points both exporters at it.
 *
 * <p><b>Ready-made:</b> {@link ObservationChatModelListener} ({@code langchain4j-observation},
 * {@code @Experimental}). The Anthropic starter injects every {@link ChatModelListener} bean into
 * its {@code ChatModel}, so declaring it is all the wiring needed. Per model call it records:
 * <ul>
 *   <li>an observation {@code gen_ai.client.operation.duration} with span name
 *       {@code chat <model>}, low-cardinality {@code gen_ai.provider.name},
 *       {@code gen_ai.request.model}, {@code gen_ai.response.model}, {@code outcome}, and
 *       high-cardinality {@code input_tokens} / {@code output_tokens};</li>
 *   <li>the histogram {@code gen_ai.client.token.usage}, tagged {@code gen_ai.token.type}
 *       {@code input}/{@code output} (no Anthropic cache-token split).</li>
 * </ul>
 * It never records prompt or completion text, and its constructor takes no convention, so that
 * can't be added to it. It opens its scope in {@code onRequest} and closes it in
 * {@code onResponse}, which is only safe when both run on the same thread: fine for this blocking
 * {@code ChatModel}, not for a {@code StreamingChatModel}. Don't also register
 * {@code MicrometerMetricsChatModelListener}: it records the same token metric, so tokens would be
 * counted twice.
 *
 * <p><b>Our own:</b> {@link RetrievalObservationListener} and {@link AiServiceObservations}, for
 * the steps that have a LangChain4j hook but no Micrometer implementation. Spring AI observes all
 * of these steps by itself.
 */
@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityConfig {

    @Bean
    ChatModelListener observationChatModelListener(ObservationRegistry observationRegistry, MeterRegistry meterRegistry) {
        return new ObservationChatModelListener(observationRegistry, meterRegistry);
    }

    @Bean
    RetrievalObservationListener retrievalObservationListener(ObservationRegistry observationRegistry,
                                                              ObservabilityProperties properties) {
        return new RetrievalObservationListener(observationRegistry, properties.includeContent());
    }

    @Bean
    AiServiceObservations aiServiceObservations(ObservationRegistry observationRegistry, ObservabilityProperties properties) {
        return new AiServiceObservations(observationRegistry, properties.includeContent());
    }
}
