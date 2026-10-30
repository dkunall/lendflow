package com.lendflow.decision.domain;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;

/**
 * Everything a credit decision for one product depends on, versioned as a unit. A published version is
 * never edited; a change is a new version.
 */
public record Ruleset(Product product, int version, Parameters parameters, String championScorecard,
                      String challengerScorecard, DecisionTable eligibility, DecisionTable decision,
                      DecisionTable pricing) {

    /**
     * @param foirCap share of monthly income all EMIs together may take, new one included
     * @param maxLtv loan-to-value cap for secured products; null when unsecured
     * @param autoApproveLimit approved amounts above this always go to an underwriter
     */
    public record Parameters(BigDecimal foirCap, BigDecimal maxLtv, BigDecimal minAmount, BigDecimal maxAmount,
                             BigDecimal amountStep, BigDecimal autoApproveLimit, int reasonLimit) {
    }

    public Ruleset {
        require(eligibility, HitPolicy.COLLECT, "eligibility");
        require(decision, HitPolicy.PRIORITY, "decision");
        require(pricing, HitPolicy.FIRST, "pricing");
        if (product.isSecured() != (parameters.maxLtv() != null)) {
            throw new IllegalArgumentException("maxLtv is required for secured products and only for them");
        }
    }

    private static void require(DecisionTable table, HitPolicy policy, String name) {
        if (table == null || table.hitPolicy() != policy) {
            throw new IllegalArgumentException(name + " table must use " + policy);
        }
    }

    public String key() {
        return product + "-v" + version;
    }
}
