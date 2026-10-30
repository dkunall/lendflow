package com.lendflow.bureau.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The parts of a bureau report the credit rules use. A no-hit report has {@code hit == false} and no score:
 * the applicant has no credit history, which is a different situation from a bad one.
 */
public record CreditReport(UUID applicationId, String bureauRef, boolean hit, Integer score, int activeAccounts,
                           BigDecimal totalOutstanding, int maxDpdLast12m, int enquiriesLast6m, int monthsOnBureau,
                           boolean writtenOff, BigDecimal reportedEmi, String consentVersion, Instant pulledAt) {

    public static CreditReport noHit(UUID applicationId, String bureauRef, String consentVersion, Instant pulledAt) {
        return new CreditReport(applicationId, bureauRef, false, null, 0, BigDecimal.ZERO, 0, 0, 0, false,
            BigDecimal.ZERO, consentVersion, pulledAt);
    }
}
