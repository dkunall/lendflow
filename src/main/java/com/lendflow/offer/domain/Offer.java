package com.lendflow.offer.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param upfrontCharges fees the borrower pays before disbursement instead of having them deducted
 * @param netDisbursal what actually reaches the borrower's account
 */
public record Offer(UUID id, UUID applicationId, Product product, BigDecimal amount, BigDecimal annualRate,
                    int tenureMonths, BigDecimal emi, BigDecimal processingFee, BigDecimal feeGst,
                    BigDecimal upfrontCharges, BigDecimal netDisbursal, BigDecimal totalInterest,
                    BigDecimal totalRepayable, OfferStatus status, Instant validUntil, Instant createdAt,
                    Instant acceptedAt, String acceptedBy) {

    /** Stored status, except that an offer nobody accepted in time reads as expired. */
    public OfferStatus statusAt(Instant now) {
        return status == OfferStatus.OFFERED && now.isAfter(validUntil) ? OfferStatus.EXPIRED : status;
    }

    public Offer accepted(Instant at, String by) {
        return new Offer(id, applicationId, product, amount, annualRate, tenureMonths, emi, processingFee, feeGst,
            upfrontCharges, netDisbursal, totalInterest, totalRepayable, OfferStatus.ACCEPTED, validUntil, createdAt,
            at, by);
    }
}
