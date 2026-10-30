package com.lendflow.shared.money;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Reducing-balance instalment: P * r * (1+r)^n / ((1+r)^n - 1), r being the monthly rate. Worked in 34
 * significant digits and rounded to paise only at the end, so the schedule built on it drifts by at most a
 * few paise, which the last instalment absorbs.
 */
public final class Emi {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal TWELVE_HUNDRED = new BigDecimal("1200");

    private Emi() {
    }

    public static BigDecimal monthly(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (principal.signum() < 0 || months <= 0 || annualRatePercent.signum() < 0) {
            throw new IllegalArgumentException("principal, rate and tenure must be non-negative, tenure positive");
        }
        if (principal.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = monthlyRate(annualRatePercent);
        BigDecimal growth = BigDecimal.ONE.add(r, MC).pow(months, MC);
        return principal.multiply(r, MC).multiply(growth, MC)
            .divide(growth.subtract(BigDecimal.ONE, MC), MC)
            .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        return annualRatePercent.divide(TWELVE_HUNDRED, MC);
    }

    /**
     * Largest whole multiple of {@code step} whose EMI fits in {@code maxEmi}, capped at {@code ceiling}.
     * EMI grows with principal, so a binary search over step counts finds it in about 20 evaluations.
     */
    public static BigDecimal maxPrincipal(BigDecimal maxEmi, BigDecimal annualRatePercent, int months,
                                          BigDecimal ceiling, BigDecimal step) {
        long lo = 0;
        long hi = ceiling.divide(step, 0, RoundingMode.FLOOR).longValueExact();
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2;
            if (monthly(step.multiply(BigDecimal.valueOf(mid)), annualRatePercent, months).compareTo(maxEmi) <= 0) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return step.multiply(BigDecimal.valueOf(lo));
    }
}
