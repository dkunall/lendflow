package com.lendflow.disbursement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Disbursement(UUID id, UUID applicationId, UUID offerId, String product, BigDecimal amount,
                           DisbursementStatus status, String utr, Instant requestedAt, Instant sentAt, String sentBy) {

    public static Disbursement requested(UUID id, UUID applicationId, UUID offerId, String product,
                                         BigDecimal amount, Instant now) {
        return new Disbursement(id, applicationId, offerId, product, amount, DisbursementStatus.REQUESTED, null, now,
            null, null);
    }

    public Disbursement sent(Utr reference, String by, Instant now) {
        if (status != DisbursementStatus.REQUESTED) {
            throw new IllegalStateException("Disbursement " + id + " is already " + status);
        }
        return new Disbursement(id, applicationId, offerId, product, amount, DisbursementStatus.SENT, reference.value(),
            requestedAt, now, by);
    }
}
