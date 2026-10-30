package com.lendflow.offer.application;

import com.lendflow.offer.domain.Offer;
import com.lendflow.offer.domain.OfferStatus;
import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferView(UUID id, UUID applicationId, Product product, BigDecimal amount, BigDecimal annualRate,
                        int tenureMonths, BigDecimal emi, BigDecimal processingFee, BigDecimal feeGst,
                        BigDecimal upfrontCharges, BigDecimal netDisbursal, BigDecimal totalInterest,
                        BigDecimal totalRepayable, OfferStatus status, Instant validUntil, Instant createdAt,
                        Instant acceptedAt) {

    static OfferView of(Offer o, Instant now) {
        return new OfferView(o.id(), o.applicationId(), o.product(), o.amount(), o.annualRate(), o.tenureMonths(),
            o.emi(), o.processingFee(), o.feeGst(), o.upfrontCharges(), o.netDisbursal(), o.totalInterest(),
            o.totalRepayable(), o.statusAt(now), o.validUntil(), o.createdAt(), o.acceptedAt());
    }
}
