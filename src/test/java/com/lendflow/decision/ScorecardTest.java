package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.decision.domain.Condition;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.decision.domain.Scorecard.Bin;
import com.lendflow.decision.domain.Scorecard.Characteristic;
import com.lendflow.decision.domain.Scorecard.Grade;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScorecardTest {

    private final Scorecard card = new Scorecard("T-1", List.of(
        new Characteristic("bureau", "bureauScore", "LOW_SCORE", List.of(
            new Bin(Condition.parse("null"), 10, "NO_HISTORY"),
            new Bin(Condition.parse("<700"), 20, null),
            new Bin(Condition.parse(">=700"), 100, null))),
        new Characteristic("dpd", "maxDpdLast12m", "DELINQUENT", List.of(
            new Bin(Condition.parse("0"), 50, null),
            new Bin(Condition.parse(">0"), 0, null)))),
        500, 20, 40, List.of(new Grade("B", 100), new Grade("A", 140), new Grade("C", 0)));

    @Test
    void addsPointsAndGradesFromTheTop() {
        Scorecard.Result result = card.score(Map.of("bureauScore", 750, "maxDpdLast12m", 0));

        assertThat(result.score()).isEqualTo(150);
        assertThat(result.grade()).isEqualTo("A");
        assertThat(result.topShortfalls(3)).isEmpty();
    }

    @Test
    void shortfallsAreOrderedByPointsLostAndBinReasonsWin() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("bureauScore", null);
        facts.put("maxDpdLast12m", 30);

        Scorecard.Result result = card.score(facts);

        assertThat(result.score()).isEqualTo(10);
        assertThat(result.grade()).isEqualTo("C");
        assertThat(result.topShortfalls(3)).containsExactly("NO_HISTORY", "DELINQUENT");
        assertThat(result.topShortfalls(1)).containsExactly("NO_HISTORY");
    }

    @Test
    void pdHalvesOddsEveryPdoPoints() {
        Scorecard calibration = new Scorecard("T-2", List.of(new Characteristic("k", "k", "R",
            List.of(new Bin(Condition.parse("-"), 500, null)))), 500, 20, 40, List.of(new Grade("A", 0)));
        // Points equal to the base score give the base odds, 20:1 (PD 1/21); 40 more points give 40:1 (PD 1/41).
        assertThat(calibration.score(Map.of("k", 1)).probabilityOfDefault()).isEqualByComparingTo("0.04762");
        Scorecard shifted = new Scorecard("T-3", List.of(new Characteristic("k", "k", "R",
            List.of(new Bin(Condition.parse("-"), 540, null)))), 500, 20, 40, List.of(new Grade("A", 0)));
        assertThat(shifted.score(Map.of("k", 1)).probabilityOfDefault()).isEqualByComparingTo("0.02439");
    }

    @Test
    void aValueNoBinCoversIsAPolicyBugNotAZero() {
        assertThatThrownBy(() -> card.score(Map.of("bureauScore", 750, "maxDpdLast12m", "unknown")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("T-1/dpd");
    }
}
