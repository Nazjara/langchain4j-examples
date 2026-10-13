package com.nazjara.model;

import java.util.List;

public record PromptInfo(String name, String description, List<String> arguments) {
}
