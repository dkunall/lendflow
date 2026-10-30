package com.lendflow.applicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.lendflow.applicant.domain.JaroWinkler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JaroWinklerTest {

    // The textbook pairs from Winkler's paper, which every implementation is checked against.
    @ParameterizedTest
    @CsvSource({
        "MARTHA, MARHTA, 0.9611",
        "DIXON, DICKSONX, 0.8133",
        "DWAYNE, DUANE, 0.8400"
    })
    void matchesPublishedValues(String a, String b, double expected) {
        assertThat(JaroWinkler.similarity(a, b)).isCloseTo(expected, within(0.0001));
    }

    @Test
    void isSymmetric() {
        assertThat(JaroWinkler.similarity("RAHUL VERMA", "RAHUL VARMA"))
            .isEqualTo(JaroWinkler.similarity("RAHUL VARMA", "RAHUL VERMA"));
    }

    @Test
    void identicalAndDisjointStrings() {
        assertThat(JaroWinkler.similarity("PRIYA SHARMA", "PRIYA SHARMA")).isEqualTo(1.0);
        assertThat(JaroWinkler.similarity("ABC", "XYZ")).isZero();
        assertThat(JaroWinkler.similarity("", "ABC")).isZero();
    }

    @Test
    void aOneLetterTypoInAnIndianNameStaysAboveTheDuplicateThreshold() {
        assertThat(JaroWinkler.similarity("ANANYA KRISHNAN", "ANANYA KRISHANAN")).isGreaterThan(0.92);
        assertThat(JaroWinkler.similarity("ANANYA KRISHNAN", "ARJUN MEHTA")).isLessThan(0.7);
    }
}
