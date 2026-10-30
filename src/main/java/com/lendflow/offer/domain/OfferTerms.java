package com.lendflow.offer.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;

/** What the credit decision allows: the starting point for pricing an offer. */
public record OfferTerms(Product product, BigDecimal amount, BigDecimal annualRate, int tenureMonths,
                         BigDecimal processingFeePercent) {
}
