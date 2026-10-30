package com.lendflow.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Scale;
import org.junit.jupiter.api.Test;

class AmortizationTest {

    @Test
    void firstAndLastRowsOfAThreeYearPersonalLoan() {
        List<Instalment> rows = Amortization.schedule(new BigDecimal("300000"), new BigDecimal("10.50"), 36);

        assertThat(rows).hasSize(36);
        Instalment first = rows.getFirst();
        assertThat(first.payment()).isEqualByComparingTo("9750.73");
        assertThat(first.interest()).isEqualByComparingTo("2625.00");
        assertThat(first.principal()).isEqualByComparingTo("7125.73");
        assertThat(first.closing()).isEqualByComparingTo("292874.27");
        assertThat(rows.getLast().closing()).isEqualByComparingTo("0");
    }

    @Test
    void zeroRateSplitsThePrincipalAndTheLastInstalmentTakesTheRemainder() {
        List<Instalment> rows = Amortization.schedule(new BigDecimal("100000"), BigDecimal.ZERO, 3);

        assertThat(rows).extracting(Instalment::payment).map(BigDecimal::toPlainString)
            .containsExactly("33333.33", "33333.33", "33333.34");
    }

    @Property(tries = 300)
    void personalLoanSchedulesRepayExactlyThePrincipal(
            @ForAll @BigRange(min = "10000", max = "5000000") @Scale(0) BigDecimal principal,
            @ForAll @BigRange(min = "0", max = "36") @Scale(2) BigDecimal rate,
            @ForAll @IntRange(min = 1, max = 84) int months) {
        repaysExactly(principal, rate, months);
    }

    @Property(tries = 300)
    void homeLoanSchedulesRepayExactlyThePrincipal(
            @ForAll @BigRange(min = "500000", max = "50000000") @Scale(0) BigDecimal principal,
            @ForAll @BigRange(min = "0", max = "15") @Scale(2) BigDecimal rate,
            @ForAll @IntRange(min = 12, max = 360) int months) {
        repaysExactly(principal, rate, months);
    }

    private static void repaysExactly(BigDecimal principal, BigDecimal rate, int months) {
        List<Instalment> rows = Amortization.schedule(principal, rate, months);
        BigDecimal emi = rows.getFirst().payment();

        assertThat(rows).hasSize(months);
        assertThat(rows.stream().map(Instalment::principal).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(principal);
        assertThat(rows.getLast().closing()).isEqualByComparingTo("0");
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.closing().signum()).isGreaterThanOrEqualTo(0);
            assertThat(r.interest().signum()).isGreaterThanOrEqualTo(0);
            assertThat(r.payment()).isEqualByComparingTo(r.interest().add(r.principal()));
        });
        assertThat(rows.subList(0, months - 1)).allSatisfy(r -> assertThat(r.payment()).isEqualByComparingTo(emi));
        // Each month rounds the EMI and the interest by at most half a paisa each, and an error left in the
        // balance accrues interest like the balance does, so the last instalment drifts by at most one paisa
        // per month compounded at the loan rate.
        double r = rate.doubleValue() / 1200;
        double compounded = r == 0 ? months : (Math.pow(1 + r, months) - 1) / r;
        assertThat(rows.getLast().payment().subtract(emi).abs().doubleValue()).isLessThanOrEqualTo(0.01 * compounded);
    }
}
