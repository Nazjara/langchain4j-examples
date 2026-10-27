package com.nazjara.model;

public sealed interface StreamEvent {

    record Text(String text) implements StreamEvent {
    }

    record ToolCallDelta(String id, String name, String arguments) implements StreamEvent {
    }

    record ToolStarted(String name, String arguments) implements StreamEvent {
    }

    record ToolFinished(String name, String result, boolean failed) implements StreamEvent {
    }

    record Done(String finishReason) implements StreamEvent {
    }

    record Failed(String message) implements StreamEvent {
    }
}
