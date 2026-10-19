package com.nazjara.observation;

import io.micrometer.observation.Observation;

/**
 * A started observation plus the scope that makes it the thread's current observation, so that
 * observations started while it is open become its children. LangChain4j's hooks come in
 * start/end pairs, and this is what one hook hands to the other.
 *
 * <p>The scope is thread-local: it must be closed on the thread that opened it. That holds for
 * every hook here because a blocking AI Service runs retrieval, model calls and tools on the
 * caller's thread.
 *
 * <p>{@code outcome} is a low-cardinality key, so it becomes a tag on the timer metric Spring Boot
 * derives from each observation, with the same values as {@code ObservationChatModelListener}.
 */
record OpenObservation(Observation observation, Observation.Scope scope) {

    static final String OUTCOME = "outcome";

    static OpenObservation start(Observation observation) {
        observation.start();
        return new OpenObservation(observation, observation.openScope());
    }

    void succeed() {
        observation.lowCardinalityKeyValue(OUTCOME, "SUCCESS");
        close();
    }

    void fail(Throwable error) {
        if (error != null) {
            observation.error(error);
        }
        observation.lowCardinalityKeyValue(OUTCOME, "ERROR");
        close();
    }

    private void close() {
        scope.close();
        observation.stop();
    }
}
