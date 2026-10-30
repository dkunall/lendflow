package com.lendflow.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AssessmentBackoffTest {

    private final Assessment assessment = new Assessment(null, null, null, null, null, null, null, null,
        Clock.systemUTC(), new SimpleMeterRegistry(), 4, Duration.ofSeconds(2), Duration.ofSeconds(60));

    @Test
    void doublesFromTheFirstWaitUpToTheCap() {
        assertThat(assessment.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(assessment.backoff(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(assessment.backoff(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(assessment.backoff(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(assessment.backoff(200)).isEqualTo(Duration.ofSeconds(60));
    }
}
