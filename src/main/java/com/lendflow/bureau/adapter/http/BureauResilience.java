package com.lendflow.bureau.adapter.http;

import com.lendflow.shared.error.UpstreamUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retry wraps the breaker, which wraps the bulkhead. So every attempt counts towards the breaker, an open
 * breaker stops retries at once, and a full bulkhead is not retried (waiting longer would only add load).
 */
class BureauResilience {

    record Settings(int maxAttempts, Duration firstWait, int window, int minimumCalls, float failureRate,
                    Duration openFor, int maxConcurrent, Duration maxWaitForSlot) {
    }

    private final Retry retry;
    private final CircuitBreaker breaker;
    private final Bulkhead bulkhead;

    BureauResilience(Settings s) {
        this.retry = Retry.of("bureau", RetryConfig.custom()
            .maxAttempts(s.maxAttempts())
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(s.firstWait(), 2.0, 0.5))
            .retryExceptions(TransientBureauFailure.class)
            .build());
        this.breaker = CircuitBreaker.of("bureau", CircuitBreakerConfig.custom()
            .slidingWindowSize(s.window())
            .minimumNumberOfCalls(s.minimumCalls())
            .failureRateThreshold(s.failureRate())
            .waitDurationInOpenState(s.openFor())
            .recordExceptions(TransientBureauFailure.class)
            .build());
        this.bulkhead = Bulkhead.of("bureau", BulkheadConfig.custom()
            .maxConcurrentCalls(s.maxConcurrent())
            .maxWaitDuration(s.maxWaitForSlot())
            .build());
    }

    <T> T call(Supplier<T> call) {
        Supplier<T> guarded = Retry.decorateSupplier(retry,
            CircuitBreaker.decorateSupplier(breaker, Bulkhead.decorateSupplier(bulkhead, call)));
        try {
            return guarded.get();
        } catch (TransientBureauFailure e) {
            throw new UpstreamUnavailableException("bureau-unavailable", "Bureau failed after "
                + retry.getRetryConfig().getMaxAttempts() + " attempts: " + e.getMessage());
        } catch (CallNotPermittedException e) {
            throw new UpstreamUnavailableException("bureau-circuit-open", "Bureau calls paused after repeated failures");
        } catch (BulkheadFullException e) {
            throw new UpstreamUnavailableException("bureau-busy", "Too many bureau pulls in flight");
        }
    }

    CircuitBreaker breaker() {
        return breaker;
    }
}
