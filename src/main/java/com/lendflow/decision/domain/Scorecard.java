package com.lendflow.decision.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A points scorecard. Each characteristic puts a fact into the first bin that matches and earns that bin's
 * points; the total is the score. The calibration says what the score means: {@code baseScore} points
 * correspond to good:bad odds of {@code baseOdds}, and every {@code pdo} points double the odds.
 */
public record Scorecard(String id, List<Characteristic> characteristics, int baseScore, double baseOdds, int pdo,
                        List<Grade> grades) {

    /** A bin may carry its own reason when the characteristic's general one would mislead. */
    public record Bin(Condition when, int points, String reasonCode) {
    }

    public record Characteristic(String name, String fact, String reasonCode, List<Bin> bins) {

        public Characteristic {
            bins = List.copyOf(bins);
            if (bins.isEmpty()) {
                throw new IllegalArgumentException(name + " has no bins");
            }
        }

        int maxPoints() {
            return bins.stream().mapToInt(Bin::points).max().orElseThrow();
        }
    }

    public record Grade(String name, int minScore) {
    }

    public record Contribution(String characteristic, Object value, int points, int shortfall, String reasonCode) {
    }

    public record Result(String scorecardId, int score, String grade, BigDecimal probabilityOfDefault,
                         List<Contribution> contributions) {

        /** Reason codes for the characteristics that cost the most points, largest gap first. */
        public List<String> topShortfalls(int limit) {
            return contributions.stream()
                .filter(c -> c.shortfall() > 0)
                .sorted(Comparator.comparingInt(Contribution::shortfall).reversed())
                .limit(limit)
                .map(Contribution::reasonCode)
                .toList();
        }
    }

    public Scorecard {
        characteristics = List.copyOf(characteristics);
        grades = grades.stream().sorted(Comparator.comparingInt(Grade::minScore).reversed()).toList();
        if (grades.isEmpty() || pdo <= 0 || baseOdds <= 0) {
            throw new IllegalArgumentException(id + ": needs grades and a positive pdo and base odds");
        }
    }

    public Result score(Map<String, Object> facts) {
        List<Contribution> contributions = new ArrayList<>();
        int total = 0;
        for (Characteristic c : characteristics) {
            Object value = facts.get(c.fact());
            Bin bin = c.bins().stream().filter(b -> b.when().matches(value)).findFirst()
                .orElseThrow(() -> new IllegalStateException(id + "/" + c.name() + ": no bin for " + value));
            total += bin.points();
            contributions.add(new Contribution(c.name(), value, bin.points(), c.maxPoints() - bin.points(),
                bin.reasonCode() != null ? bin.reasonCode() : c.reasonCode()));
        }
        return new Result(id, total, grade(total), probabilityOfDefault(total), List.copyOf(contributions));
    }

    String grade(int score) {
        return grades.stream().filter(g -> score >= g.minScore()).findFirst().orElse(grades.getLast()).name();
    }

    /** odds = baseOdds * 2^((score - baseScore) / pdo); PD = 1 / (1 + odds). */
    BigDecimal probabilityOfDefault(int score) {
        double odds = baseOdds * Math.pow(2, (score - baseScore) / (double) pdo);
        return BigDecimal.valueOf(1 / (1 + odds)).round(new MathContext(4, RoundingMode.HALF_UP));
    }
}
