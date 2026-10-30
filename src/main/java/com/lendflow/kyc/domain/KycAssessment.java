package com.lendflow.kyc.domain;

import com.lendflow.applicant.domain.JaroWinkler;
import com.lendflow.applicant.domain.PersonalDetails;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a PAN registry answer into a KYC outcome. Names on PAN records are often abbreviated or ordered
 * differently from what applicants type, so a middling similarity goes to a person rather than failing.
 */
public record KycAssessment(KycOutcome outcome, double nameScore, List<String> reasons) {

    public static final String PAN_INVALID = "KYC_PAN_INVALID";
    public static final String NAME_MISMATCH = "KYC_NAME_MISMATCH";
    public static final String NAME_PARTIAL_MATCH = "KYC_NAME_PARTIAL_MATCH";

    public static KycAssessment assess(PanVerification pan, String declaredName, double passAt, double referAt) {
        if (!pan.valid()) {
            return new KycAssessment(KycOutcome.FAIL, 0.0, List.of(PAN_INVALID));
        }
        double score = bestOrderScore(PersonalDetails.comparableName(declaredName),
            PersonalDetails.comparableName(pan.nameOnRecord()));
        if (score >= passAt) {
            return new KycAssessment(KycOutcome.PASS, score, List.of());
        }
        if (score >= referAt) {
            return new KycAssessment(KycOutcome.REFER, score, List.of(NAME_PARTIAL_MATCH));
        }
        return new KycAssessment(KycOutcome.FAIL, score, List.of(NAME_MISMATCH));
    }

    // "VERMA RAHUL" on the PAN and "RAHUL VERMA" on the form are the same person.
    private static double bestOrderScore(String declared, String onRecord) {
        double direct = JaroWinkler.similarity(declared, onRecord);
        double sorted = JaroWinkler.similarity(sortTokens(declared), sortTokens(onRecord));
        return Math.max(direct, sorted);
    }

    private static String sortTokens(String name) {
        return String.join(" ", Arrays.stream(name.split(" ")).sorted().toList());
    }
}
