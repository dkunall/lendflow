package com.lendflow.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.shared.money.Emi;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EmiTest {

    // Reference values from the closed-form formula, checked independently with Python's decimal module.
    @ParameterizedTest
    @CsvSource({
        "100000, 12, 12, 8884.88",
        "500000, 10.5, 60, 10746.95",
        "2500000, 8.5, 240, 21695.58",
        "120000, 0, 12, 10000.00"
    })
    void matchesTheReducingBalanceFormula(String principal, String rate, int months, String expected) {
        assertThat(Emi.monthly(new BigDecimal(principal), new BigDecimal(rate), months))
            .isEqualByComparingTo(expected);
    }

    @Test
    void maxPrincipalIsTheLargestStepThatStillFits() {
        BigDecimal maxEmi = new BigDecimal("7000");
        BigDecimal best = Emi.maxPrincipal(maxEmi, new BigDecimal("10.5"), 36, new BigDecimal("300000"),
            new BigDecimal("1000"));

        assertThat(Emi.monthly(best, new BigDecimal("10.5"), 36)).isLessThanOrEqualTo(maxEmi);
        assertThat(Emi.monthly(best.add(new BigDecimal("1000")), new BigDecimal("10.5"), 36)).isGreaterThan(maxEmi);
    }

    @Test
    void maxPrincipalNeverExceedsTheCeiling() {
        assertThat(Emi.maxPrincipal(new BigDecimal("1000000"), new BigDecimal("12"), 12, new BigDecimal("250000"),
            new BigDecimal("1000"))).isEqualByComparingTo("250000");
    }
}
