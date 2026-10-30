package com.lendflow.offer.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * Home loans: the fee is capped and paid upfront, because the full amount goes to the seller and a short
 * payment would break the sale agreement.
 */
public class HomeOfferGenerator extends OfferGenerator {

    private final BigDecimal feeCap;
    private final Duration validity;

    public HomeOfferGenerator(BigDecimal gstPercent, BigDecimal feeCap, Duration validity) {
        super(gstPercent);
        this.feeCap = feeCap;
        this.validity = validity;
    }

    @Override
    public Product product() {
        return Product.HOME;
    }

    @Override
    protected BigDecimal processingFee(OfferTerms terms) {
        return percentOf(terms.amount(), terms.processingFeePercent()).min(feeCap);
    }

    @Override
    protected boolean feeDeductedFromDisbursal() {
        return false;
    }

    @Override
    protected Duration validity() {
        return validity;
    }
}
