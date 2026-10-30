package com.lendflow.decision.domain;

import com.lendflow.shared.money.Emi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Knockouts, then the scorecard, then pricing and affordability, then the decision table. Pure: the same
 * ruleset, scorecard and facts always give the same decision, which is what makes simulation and the golden
 * tests meaningful.
 */
public final class DecisionEngine {

    public static final String COUNTER_OFFER = "COUNTER_OFFER_REDUCED_AMOUNT";
    public static final String NO_RULE_MATCHED = "NO_DECISION_RULE_MATCHED";

    private DecisionEngine() {
    }

    public static CreditDecision decide(Ruleset ruleset, Scorecard scorecard, CreditFacts input) {
        Ruleset.Parameters p = ruleset.parameters();
        Map<String, Object> facts = input.asMap();
        Map<String, List<String>> fired = new LinkedHashMap<>();

        List<Rule> knockouts = ruleset.eligibility().evaluate(facts);
        fired.put("eligibility", ids(knockouts));

        Scorecard.Result score = scorecard.score(facts);
        facts.put("score", score.score());
        facts.put("grade", score.grade());

        List<Rule> priced = ruleset.pricing().evaluate(facts);
        fired.put("pricing", ids(priced));
        BigDecimal rate = null;
        BigDecimal fee = null;
        BigDecimal maxEmi = input.monthlyIncome().multiply(p.foirCap()).subtract(input.existingEmi())
            .max(BigDecimal.ZERO).setScale(2, RoundingMode.DOWN);
        BigDecimal approved = null;
        boolean reduced = false;
        if (!priced.isEmpty()) {
            rate = decimal(priced.getFirst().then().get("annualRate"));
            fee = decimal(priced.getFirst().then().get("processingFeePercent"));
            approved = Emi.maxPrincipal(maxEmi, rate, input.tenureMonths(), ceiling(p, input), p.amountStep());
            reduced = approved.compareTo(input.requestedAmount()) < 0;
            facts.put("affordable", approved.compareTo(p.minAmount()) >= 0);
            facts.put("amountReduced", reduced);
            facts.put("aboveAutoLimit", approved.compareTo(p.autoApproveLimit()) > 0);
        }

        Outcome outcome;
        Set<String> reasons = new LinkedHashSet<>();
        if (!knockouts.isEmpty()) {
            outcome = Outcome.REJECT;
            knockouts.forEach(r -> reasons.add(String.valueOf(r.then().get("reason"))));
        } else {
            List<Rule> decided = ruleset.decision().evaluate(facts);
            fired.put("decision", ids(decided));
            if (decided.isEmpty()) {
                outcome = Outcome.REFER;
                reasons.add(NO_RULE_MATCHED);
            } else {
                outcome = Outcome.valueOf(String.valueOf(decided.getFirst().then().get("outcome")));
                decided.forEach(r -> reasons.add(String.valueOf(r.then().get("reason"))));
            }
        }
        if (outcome != Outcome.APPROVE) {
            reasons.addAll(score.topShortfalls(p.reasonLimit()));
        }
        if (outcome != Outcome.REJECT && reduced) {
            reasons.add(COUNTER_OFFER);
        }
        boolean offer = outcome != Outcome.REJECT && approved != null;
        return new CreditDecision(ruleset.key(), outcome, new ArrayList<>(reasons), score,
            offer ? rate : null, offer ? fee : null, maxEmi,
            offer ? approved : null,
            offer ? Emi.monthly(approved, rate, input.tenureMonths()) : null,
            fired);
    }

    private static BigDecimal ceiling(Ruleset.Parameters p, CreditFacts input) {
        BigDecimal ceiling = input.requestedAmount().min(p.maxAmount());
        if (p.maxLtv() != null) {
            ceiling = ceiling.min(input.propertyValue().multiply(p.maxLtv()));
        }
        return ceiling;
    }

    private static List<String> ids(List<Rule> rules) {
        return rules.stream().map(Rule::id).toList();
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
