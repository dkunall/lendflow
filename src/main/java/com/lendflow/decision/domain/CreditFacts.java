package com.lendflow.decision.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Inputs to a decision, gathered from the application, KYC and the bureau. The existing EMI is the larger of
 * what the applicant declared and what the bureau reports, since either can be stale.
 */
public record CreditFacts(int age, int ageAtMaturity, String employmentType, int monthsInCurrentJob,
                          BigDecimal monthlyIncome, BigDecimal declaredEmi, BigDecimal requestedAmount,
                          int tenureMonths, BigDecimal propertyValue, String kyc, boolean bureauHit,
                          Integer bureauScore, int maxDpdLast12m, int enquiriesLast6m, int monthsOnBureau,
                          boolean writtenOff, BigDecimal reportedEmi, boolean duplicateSuspected) {

    public BigDecimal existingEmi() {
        return declaredEmi.max(reportedEmi);
    }

    public BigDecimal foir() {
        return existingEmi().divide(monthlyIncome, 4, RoundingMode.HALF_UP);
    }

    /** The names rule and scorecard conditions refer to. Kept flat and explicit so a ruleset can be audited. */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("age", age);
        m.put("ageAtMaturity", ageAtMaturity);
        m.put("employmentType", employmentType);
        m.put("monthsInCurrentJob", monthsInCurrentJob);
        m.put("monthlyIncome", monthlyIncome);
        m.put("existingEmi", existingEmi());
        m.put("foir", foir());
        m.put("requestedAmount", requestedAmount);
        m.put("tenureMonths", tenureMonths);
        m.put("propertyValue", propertyValue);
        m.put("kyc", kyc);
        m.put("bureauHit", bureauHit);
        m.put("bureauScore", bureauScore);
        m.put("maxDpdLast12m", maxDpdLast12m);
        m.put("enquiriesLast6m", enquiriesLast6m);
        m.put("monthsOnBureau", monthsOnBureau);
        m.put("writtenOff", writtenOff);
        m.put("duplicateSuspected", duplicateSuspected);
        return m;
    }
}
