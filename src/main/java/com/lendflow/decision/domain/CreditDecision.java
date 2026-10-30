package com.lendflow.decision.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The result and enough of the working to explain it: which rules fired in each table, the scorecard
 * breakdown and the affordability numbers. Offer fields are null when nothing could be priced.
 */
public record CreditDecision(String ruleset, Outcome outcome, List<String> reasons, Scorecard.Result score,
                             BigDecimal annualRate, BigDecimal processingFeePercent, BigDecimal maxEmi,
                             BigDecimal approvedAmount, BigDecimal emi, Map<String, List<String>> firedRules) {

    public CreditDecision {
        reasons = List.copyOf(reasons);
        firedRules = Map.copyOf(firedRules);
    }

    public boolean counterOffer() {
        return reasons.contains(DecisionEngine.COUNTER_OFFER);
    }
}
