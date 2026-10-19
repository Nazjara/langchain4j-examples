package com.nazjara.configuration;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

/**
 * Records every stopped observation, in stop order, so tests can check names, parents and key
 * values the way a tracing backend would see them.
 */
final class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

    final List<Observation.Context> stopped = new CopyOnWriteArrayList<>();

    @Override
    public void onStop(Observation.Context context) {
        stopped.add(context);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    Observation.Context named(String contextualName) {
        return stopped.stream()
                .filter(context -> contextualName.equals(context.getContextualName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No observation " + contextualName + " in " + contextualNames()));
    }

    List<String> contextualNames() {
        return stopped.stream().map(Observation.Context::getContextualName).toList();
    }

    static String parentName(Observation.Context context) {
        var parent = context.getParentObservation();
        return parent == null ? null : parent.getContextView().getContextualName();
    }

    static String low(Observation.Context context, String key) {
        var keyValue = context.getLowCardinalityKeyValue(key);
        return keyValue == null ? null : keyValue.getValue();
    }

    static String high(Observation.Context context, String key) {
        var keyValue = context.getHighCardinalityKeyValue(key);
        return keyValue == null ? null : keyValue.getValue();
    }
}
