package com.lendflow.offer.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;

/** Unsecured loans: the fee has a floor and comes out of the disbursed amount. */
public class PersonalOfferGenerator extends OfferGenerator {

    private final BigDecimal minimumFee;
    private final Duration validity;

    public PersonalOfferGenerator(BigDecimal gstPercent, BigDecimal minimumFee, Duration validity) {
        super(gstPercent);
        this.minimumFee = minimumFee;
        this.validity = validity;
    }

    @Override
    public Product product() {
        return Product.PERSONAL;
    }

    @Override
    protected BigDecimal processingFee(OfferTerms terms) {
        return percentOf(terms.amount(), terms.processingFeePercent()).max(minimumFee);
    }

    @Override
    protected boolean feeDeductedFromDisbursal() {
        return true;
    }

    @Override
    protected Duration validity() {
        return validity;
    }
}
