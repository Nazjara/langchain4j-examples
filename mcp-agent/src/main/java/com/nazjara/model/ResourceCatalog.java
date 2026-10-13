package com.nazjara.model;

import java.util.List;

public record ResourceCatalog(List<ResourceInfo> resources, List<ResourceInfo> templates) {
}
