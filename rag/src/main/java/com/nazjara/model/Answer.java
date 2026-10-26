package com.nazjara.model;

import java.util.List;

public record Answer(String answer, List<Source> sources) {
}
