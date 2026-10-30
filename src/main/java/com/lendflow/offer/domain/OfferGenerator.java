package com.lendflow.offer.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Prices an offer in fixed steps: fee, tax on the fee, how the fee is collected, repayment totals and how long
 * the offer stays open. Products differ only in the fee rule, the collection and the validity.
 */
public abstract class OfferGenerator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BigDecimal gstPercent;

    protected OfferGenerator(BigDecimal gstPercent) {
        this.gstPercent = gstPercent;
    }

    public abstract Product product();

    public final Offer generate(UUID id, UUID applicationId, OfferTerms terms, Instant now) {
        if (terms.product() != product()) {
            throw new IllegalArgumentException(terms.product() + " terms given to the " + product() + " generator");
        }
        BigDecimal fee = processingFee(terms).setScale(2, RoundingMode.HALF_UP);
        BigDecimal gst = fee.multiply(gstPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal charges = fee.add(gst);
        boolean deducted = feeDeductedFromDisbursal();
        List<Instalment> schedule = Amortization.schedule(terms.amount(), terms.annualRate(), terms.tenureMonths());
        BigDecimal interest = Amortization.totalInterest(schedule);
        return new Offer(id, applicationId, product(), terms.amount().setScale(2, RoundingMode.UNNECESSARY),
            terms.annualRate(), terms.tenureMonths(), schedule.getFirst().payment(), fee, gst,
            deducted ? BigDecimal.ZERO.setScale(2) : charges,
            deducted ? terms.amount().subtract(charges).setScale(2, RoundingMode.UNNECESSARY)
                : terms.amount().setScale(2, RoundingMode.UNNECESSARY),
            interest, terms.amount().add(interest).setScale(2, RoundingMode.UNNECESSARY), OfferStatus.OFFERED,
            now.plus(validity()), now, null, null);
    }

    protected abstract BigDecimal processingFee(OfferTerms terms);

    protected abstract boolean feeDeductedFromDisbursal();

    protected abstract Duration validity();

    protected static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
