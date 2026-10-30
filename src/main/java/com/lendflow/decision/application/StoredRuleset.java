package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.shared.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record StoredRuleset(UUID id, Product product, int version, RulesetStatus status, JsonNode definition,
                            String createdBy, Instant createdAt, String publishedBy, Instant publishedAt) {

    public String key() {
        return product + "-v" + version;
    }
}
