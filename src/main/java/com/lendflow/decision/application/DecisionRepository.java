package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.decision.domain.Outcome;
import com.lendflow.shared.domain.Product;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository {

    /** Keeps the first decision for an application; returns false when one was already there. */
    boolean insertFirst(DecisionView decision);

    Optional<DecisionView> find(UUID applicationId);

    void saveShadow(ShadowScore shadow);

    List<ShadowReport.Cell> shadowCells(String rulesetKey);

    record RecordedInputs(UUID applicationId, Outcome outcome, JsonNode inputs) {
    }

    /** The most recent decisions for a product, newest first. */
    List<RecordedInputs> recentInputs(Product product, int limit);
}
