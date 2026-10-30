package com.lendflow.decision.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository {

    /** Keeps the first decision for an application; returns false when one was already there. */
    boolean insertFirst(DecisionView decision);

    Optional<DecisionView> find(UUID applicationId);

    void saveShadow(ShadowScore shadow);

    List<ShadowReport.Cell> shadowCells(String rulesetKey);
}
