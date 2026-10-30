package com.lendflow.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.kyc.domain.KycAssessment;
import com.lendflow.kyc.domain.KycOutcome;
import com.lendflow.kyc.domain.PanVerification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KycAssessmentTest {

    @ParameterizedTest
    @CsvSource({
        "Rahul Verma, RAHUL VERMA, PASS",
        "Rahul Verma, VERMA RAHUL, PASS",
        "Dr. Neha Kapoor, NEHA KAPOOR, PASS",
        "Anita Desai, A DESAI, REFER",
        "Priya Sharma, MOHAN LAL GUPTA, FAIL"
    })
    void comparesTheDeclaredNameWithTheRegistry(String declared, String onRecord, KycOutcome expected) {
        assertThat(KycAssessment.assess(new PanVerification(true, onRecord), declared, 0.85, 0.70).outcome())
            .isEqualTo(expected);
    }

    @Test
    void anUnknownPanFailsWithItsOwnReason() {
        KycAssessment result = KycAssessment.assess(new PanVerification(false, null), "Rahul Verma", 0.85, 0.70);

        assertThat(result.outcome()).isEqualTo(KycOutcome.FAIL);
        assertThat(result.reasons()).containsExactly(KycAssessment.PAN_INVALID);
    }

    @Test
    void aPartialMatchSaysWhyItWasReferred() {
        KycAssessment result = KycAssessment.assess(new PanVerification(true, "A DESAI"), "Anita Desai", 0.85, 0.70);

        assertThat(result.reasons()).containsExactly(KycAssessment.NAME_PARTIAL_MATCH);
        assertThat(result.nameScore()).isBetween(0.70, 0.85);
    }
}
