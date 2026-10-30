package com.lendflow.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.decision.domain.Condition;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConditionTest {

    @Test
    void comparisonsWorkAcrossNumberTypes() {
        assertThat(Condition.parse("<650").matches(649)).isTrue();
        assertThat(Condition.parse("<650").matches(650)).isFalse();
        assertThat(Condition.parse(">=0.5").matches(new BigDecimal("0.50"))).isTrue();
        assertThat(Condition.parse("<=10").matches(10L)).isTrue();
        assertThat(Condition.parse("5").matches(new BigDecimal("5.00"))).isTrue();
    }

    @Test
    void rangeBracketsDecideWhetherTheEndsAreIncluded() {
        Condition closed = Condition.parse("[21..60]");
        Condition halfOpen = Condition.parse("[0.2..0.4)");

        assertThat(closed.matches(21)).isTrue();
        assertThat(closed.matches(60)).isTrue();
        assertThat(closed.matches(61)).isFalse();
        assertThat(halfOpen.matches(new BigDecimal("0.2"))).isTrue();
        assertThat(halfOpen.matches(new BigDecimal("0.4"))).isFalse();
    }

    @Test
    void namesBooleansAndMissingValues() {
        assertThat(Condition.parse("A,B").matches("B")).isTrue();
        assertThat(Condition.parse("A,B").matches("C")).isFalse();
        assertThat(Condition.parse("true").matches(true)).isTrue();
        assertThat(Condition.parse("true").matches(null)).isFalse();
        assertThat(Condition.parse("null").matches(null)).isTrue();
        assertThat(Condition.parse("-").matches(null)).isTrue();
        assertThat(Condition.parse("<650").matches(null)).isFalse();
    }

    @Test
    void typosFailWhenTheTableIsLoaded() {
        assertThatThrownBy(() -> Condition.parse("=<650")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Condition.parse("[60..21]")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Condition.parse("")).isInstanceOf(IllegalArgumentException.class);
    }
}
