package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.decision.domain.Outcome;
import com.lendflow.decision.domain.Scorecard;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A stored decision with its full working, as underwriters and auditors see it. */
public record DecisionView(UUID applicationId, UUID rulesetId, String ruleset, String scorecard, Outcome outcome,
                           List<String> reasons, int score, String grade, BigDecimal probabilityOfDefault,
                           List<Scorecard.Contribution> contributions, Map<String, List<String>> firedRules,
                           BigDecimal annualRate, BigDecimal processingFeePercent, BigDecimal maxEmi,
                           BigDecimal approvedAmount, BigDecimal emi, JsonNode inputs, Instant decidedAt) {

    /** What the applicant is told: the outcome, the reasons behind it and the terms, not the scoring detail. */
    public Map<String, Object> forApplicant() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("applicationId", applicationId);
        view.put("outcome", outcome);
        view.put("reasons", reasons);
        if (outcome != Outcome.REJECT && approvedAmount != null) {
            view.put("approvedAmount", approvedAmount);
            view.put("annualRate", annualRate);
            view.put("emi", emi);
        }
        view.put("decidedAt", decidedAt);
        return view;
    }
}
