package com.lendflow.decision.application;

import java.time.Instant;
import java.util.UUID;

public record ShadowScore(UUID applicationId, String rulesetKey, String championId, String championGrade,
                          int championScore, String challengerId, String challengerGrade, int challengerScore,
                          Instant scoredAt) {
}
