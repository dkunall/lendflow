package com.lendflow.bureau.adapter.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.shared.error.UpstreamUnavailableException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BureauResilienceTest {

    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void transientFailuresAreRetriedThenReported() {
        BureauResilience resilience = new BureauResilience(settings(3, 10, 10, 8, Duration.ZERO));

        assertThatThrownBy(() -> resilience.call(this::failTransiently))
            .isInstanceOfSatisfying(UpstreamUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("bureau-unavailable"));
        assertThat(calls).hasValue(3);
    }

    @Test
    void aRejectedRequestIsNotRetried() {
        BureauResilience resilience = new BureauResilience(settings(3, 10, 10, 8, Duration.ZERO));

        assertThatThrownBy(() -> resilience.call(() -> {
            calls.incrementAndGet();
            throw new UpstreamUnavailableException("bureau-rejected", "HTTP 400");
        })).hasMessageContaining("HTTP 400");
        assertThat(calls).hasValue(1);
    }

    @Test
    void repeatedFailuresOpenTheBreakerAndLaterCallsFailFast() {
        BureauResilience resilience = new BureauResilience(settings(1, 4, 4, 8, Duration.ZERO));
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> resilience.call(this::failTransiently));
        }

        assertThatThrownBy(() -> resilience.call(() -> calls.incrementAndGet()))
            .isInstanceOfSatisfying(UpstreamUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("bureau-circuit-open"));
        assertThat(calls).hasValue(4);
    }

    @Test
    void pullsBeyondTheConcurrencyCapAreTurnedAway() throws Exception {
        BureauResilience resilience = new BureauResilience(settings(3, 10, 10, 1, Duration.ZERO));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(() -> resilience.call(() -> {
            inside.countDown();
            await(release);
            return 1;
        }));
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> resilience.call(() -> 2))
            .isInstanceOfSatisfying(UpstreamUnavailableException.class,
                e -> assertThat(e.code()).isEqualTo("bureau-busy"));
        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(1);
    }

    private Integer failTransiently() {
        calls.incrementAndGet();
        throw new TransientBureauFailure("HTTP 503");
    }

    private static BureauResilience.Settings settings(int attempts, int window, int minimumCalls, int maxConcurrent,
                                                      Duration maxWait) {
        return new BureauResilience.Settings(attempts, Duration.ofMillis(1), window, minimumCalls, 50f,
            Duration.ofMinutes(1), maxConcurrent, maxWait);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
