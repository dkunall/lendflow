package com.lendflow.kyc.application;

import com.lendflow.kyc.domain.KycOutcome;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KycResult(UUID applicationId, KycOutcome outcome, double nameScore, List<String> reasons,
                        Instant checkedAt) {
}
