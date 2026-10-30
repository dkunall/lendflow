package com.lendflow.applicant.application;

import com.lendflow.applicant.domain.DuplicateSignal;
import java.util.Set;
import java.util.UUID;

public record DuplicateMatch(UUID otherApplicationId, Set<DuplicateSignal> signals, double nameSimilarity) {
}
