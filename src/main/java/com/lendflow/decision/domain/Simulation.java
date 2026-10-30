package com.lendflow.decision.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Replays recorded decision inputs through another ruleset. Inputs are the facts as they were on the day, so the
 * comparison isolates the policy change from drift in bureau data or applicant ages.
 */
public final class Simulation {

    public record Past(UUID applicationId, Outcome outcome, CreditFacts facts) {
    }

    public record Change(UUID applicationId, Outcome from, Outcome to, List<String> reasons) {
    }

    public record Report(int evaluated, Map<Outcome, Map<Outcome, Integer>> matrix, int changed, List<Change> sample) {
    }

    private Simulation() {
    }

    public static Report run(Ruleset ruleset, Scorecard scorecard, List<Past> history, int sampleSize) {
        Map<Outcome, Map<Outcome, Integer>> matrix = new EnumMap<>(Outcome.class);
        List<Change> sample = new ArrayList<>();
        int changed = 0;
        for (Past past : history) {
            CreditDecision now = DecisionEngine.decide(ruleset, scorecard, past.facts());
            matrix.computeIfAbsent(past.outcome(), o -> new EnumMap<>(Outcome.class))
                .merge(now.outcome(), 1, Integer::sum);
            if (now.outcome() != past.outcome()) {
                changed++;
                if (sample.size() < sampleSize) {
                    sample.add(new Change(past.applicationId(), past.outcome(), now.outcome(), now.reasons()));
                }
            }
        }
        return new Report(history.size(), matrix, changed, sample);
    }
}
