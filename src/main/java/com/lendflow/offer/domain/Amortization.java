package com.lendflow.offer.domain;

import com.lendflow.shared.money.Emi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducing-balance schedule. Interest is rounded to paise every month, so after n rounded EMIs a few paise
 * are left over or owed; the last instalment settles whatever balance remains instead of paying the EMI.
 */
public final class Amortization {

    private Amortization() {
    }

    public static List<Instalment> schedule(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        BigDecimal emi = Emi.monthly(principal, annualRatePercent, months);
        BigDecimal rate = Emi.monthlyRate(annualRatePercent);
        BigDecimal balance = principal.setScale(2, RoundingMode.UNNECESSARY);
        List<Instalment> rows = new ArrayList<>(months);
        for (int n = 1; n <= months; n++) {
            BigDecimal interest = balance.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal payment = n == months ? balance.add(interest) : emi.min(balance.add(interest));
            BigDecimal principalPart = payment.subtract(interest);
            BigDecimal closing = balance.subtract(principalPart);
            rows.add(new Instalment(n, balance, payment, interest, principalPart, closing));
            balance = closing;
        }
        return rows;
    }

    public static BigDecimal totalInterest(List<Instalment> schedule) {
        return schedule.stream().map(Instalment::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
