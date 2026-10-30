package com.lendflow.offer.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SanctionLetterTest {

    @ParameterizedTest
    @CsvSource({
        "0, INR 0.00",
        "540, INR 540.00",
        "9750.73, 'INR 9,750.73'",
        "296460, 'INR 2,96,460.00'",
        "5600000, 'INR 56,00,000.00'",
        "123456789.5, 'INR 12,34,56,789.50'"
    })
    void groupsInLakhsAndCrores(String amount, String expected) {
        assertThat(SanctionLetter.rupees(new BigDecimal(amount))).isEqualTo(expected);
    }
}
