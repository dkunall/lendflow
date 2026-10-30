package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.shared.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyRepository {

    /** Inserts unless a scorecard with this id already exists; returns whether a row was added. */
    boolean addScorecard(String id, JsonNode definition);

    Optional<JsonNode> scorecard(String id);

    /** Inserts unless the version, or a published version for the product, already exists. */
    boolean seedPublished(UUID id, Product product, int version, JsonNode definition);

    void insertDraft(StoredRuleset draft);

    Optional<StoredRuleset> find(UUID id);

    Optional<StoredRuleset> published(Product product);

    List<StoredRuleset> all(Product product);

    int nextVersion(Product product);
}
