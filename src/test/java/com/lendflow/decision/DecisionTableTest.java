package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.decision.domain.Condition;
import com.lendflow.decision.domain.DecisionTable;
import com.lendflow.decision.domain.HitPolicy;
import com.lendflow.decision.domain.Rule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionTableTest {

    private static Rule rule(String id, String grade, String dpd, String outcome) {
        return new Rule(id, List.of(Condition.parse(grade), Condition.parse(dpd)), Map.of("outcome", outcome));
    }

    private static DecisionTable table(HitPolicy policy) {
        return new DecisionTable("t", policy, List.of("grade", "dpd"), List.of("outcome"),
            policy == HitPolicy.PRIORITY ? "outcome" : null, List.of("REJECT", "REFER", "APPROVE"),
            List.of(rule("R1", "A,B", "-", "APPROVE"), rule("R2", "-", ">30", "REFER"), rule("R3", "-", ">60", "REJECT"),
                rule("R4", "-", ">90", "REJECT")));
    }

    @Test
    void firstTakesTableOrder() {
        assertThat(table(HitPolicy.FIRST).evaluate(Map.of("grade", "A", "dpd", 95)))
            .extracting(Rule::id).containsExactly("R1");
    }

    @Test
    void collectReturnsEveryMatch() {
        assertThat(table(HitPolicy.COLLECT).evaluate(Map.of("grade", "A", "dpd", 95)))
            .extracting(Rule::id).containsExactly("R1", "R2", "R3", "R4");
    }

    @Test
    void priorityKeepsEveryRuleTiedAtTheTop() {
        assertThat(table(HitPolicy.PRIORITY).evaluate(Map.of("grade", "A", "dpd", 95)))
            .extracting(Rule::id).containsExactly("R3", "R4");
        assertThat(table(HitPolicy.PRIORITY).evaluate(Map.of("grade", "A", "dpd", 40)))
            .extracting(Rule::id).containsExactly("R2");
        assertThat(table(HitPolicy.PRIORITY).evaluate(Map.of("grade", "D", "dpd", 0))).isEmpty();
    }

    @Test
    void malformedRulesAreRejectedUpFront() {
        assertThatThrownBy(() -> new DecisionTable("t", HitPolicy.FIRST, List.of("grade", "dpd"), List.of("outcome"),
            null, null, List.of(new Rule("R1", List.of(Condition.ANY), Map.of("outcome", "APPROVE")))))
            .hasMessageContaining("expected 2 conditions");
        assertThatThrownBy(() -> new DecisionTable("t", HitPolicy.PRIORITY, List.of("grade", "dpd"), List.of("outcome"),
            "outcome", List.of("REJECT"), List.of(rule("R1", "-", "-", "APPROVE"))))
            .hasMessageContaining("not in the priority list");
    }
}
