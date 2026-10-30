package com.lendflow.workflow.application;

import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.application.ApplicationTransitions;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.bureau.application.BureauService;
import com.lendflow.decision.application.DecisionService;
import com.lendflow.decision.application.DecisionView;
import com.lendflow.kyc.application.KycService;
import com.lendflow.shared.error.UpstreamUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the checks for one submitted application and routes it. KYC and the bureau are independent, so they
 * run side by side. If either provider is down the job is retried with backoff; once attempts run out the
 * application goes to an underwriter with the reason rather than waiting forever.
 */
@Service
public class Assessment {

    private static final Logger log = LoggerFactory.getLogger(Assessment.class);
    static final String ACTOR = "system:workflow";

    public enum Result { ROUTED, RETRY, SKIPPED, FAILED }

    private final ApplicationService applications;
    private final ApplicationTransitions transitions;
    private final KycService kyc;
    private final BureauService bureau;
    private final DecisionService decisions;
    private final JobRepository jobs;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry meters;
    private final int maxAttempts;
    private final Duration firstBackoff;
    private final Duration maxBackoff;

    Assessment(ApplicationService applications, ApplicationTransitions transitions, KycService kyc,
               BureauService bureau, DecisionService decisions, JobRepository jobs, TransactionTemplate tx,
               Clock clock, MeterRegistry meters,
               @Value("${lendflow.workflow.max-attempts:4}") int maxAttempts,
               @Value("${lendflow.workflow.first-backoff:2s}") Duration firstBackoff,
               @Value("${lendflow.workflow.max-backoff:60s}") Duration maxBackoff) {
        this.applications = applications;
        this.transitions = transitions;
        this.kyc = kyc;
        this.bureau = bureau;
        this.decisions = decisions;
        this.jobs = jobs;
        this.tx = tx;
        this.clock = clock;
        this.meters = meters;
        this.maxAttempts = maxAttempts;
        this.firstBackoff = firstBackoff;
        this.maxBackoff = maxBackoff;
    }

    public Result run(AssessmentJob job) {
        Result result;
        try {
            result = assess(job);
        } catch (RuntimeException e) {
            log.error("Assessment of {} failed and needs a look", job.applicationId(), e);
            jobs.fail(job.id(), e.getClass().getSimpleName() + ": " + e.getMessage());
            result = Result.FAILED;
        }
        meters.counter("lendflow.workflow.assessments", "result", result.name()).increment();
        return result;
    }

    private Result assess(AssessmentJob job) {
        UUID id = job.applicationId();
        List<String> unavailable = new ArrayList<>();
        try (ExecutorService checks = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> kycCheck = checks.submit(() -> kyc.check(id));
            Future<?> bureauPull = checks.submit(() -> bureau.pull(id));
            upstreamFailure(kycCheck).ifPresent(e -> unavailable(job, "KYC_UNAVAILABLE", e, unavailable));
            upstreamFailure(bureauPull).ifPresent(e -> unavailable(job, "BUREAU_UNAVAILABLE", e, unavailable));
        }
        if (!unavailable.isEmpty() && job.attempt() < maxAttempts) {
            jobs.retryLater(job.id(), clock.instant().plus(backoff(job.attempt())), String.join(",", unavailable));
            return Result.RETRY;
        }
        return tx.execute(status -> route(job, unavailable));
    }

    private Result route(AssessmentJob job, List<String> unavailable) {
        LoanApplication application = applications.find(job.applicationId());
        if (application.status() != ApplicationStatus.SUBMITTED) {
            jobs.complete(job.id());
            return Result.SKIPPED;
        }
        String checkNote = unavailable.isEmpty() ? null : String.join(",", unavailable);
        Optional<LoanApplication> checked = transitions.tryApply(application, LifecycleEvent.CHECKS_COMPLETED, ACTOR,
            checkNote);
        if (checked.isEmpty()) {
            return Result.SKIPPED;
        }
        if (!unavailable.isEmpty()) {
            transitions.apply(checked.get(), LifecycleEvent.REFER, ACTOR, checkNote);
        } else {
            DecisionView decision = decisions.decide(job.applicationId());
            LifecycleEvent event = switch (decision.outcome()) {
                case APPROVE -> LifecycleEvent.AUTO_APPROVE;
                case REFER -> LifecycleEvent.REFER;
                case REJECT -> LifecycleEvent.AUTO_REJECT;
            };
            transitions.apply(checked.get(), event, ACTOR, String.join(",", decision.reasons()));
        }
        jobs.complete(job.id());
        return Result.ROUTED;
    }

    private static void unavailable(AssessmentJob job, String reason, UpstreamUnavailableException e, List<String> into) {
        log.warn("{} for {} on attempt {}: {} {}", reason, job.applicationId(), job.attempt(), e.code(), e.getMessage());
        into.add(reason);
    }

    Duration backoff(int attempt) {
        Duration wait = firstBackoff.multipliedBy(1L << Math.min(attempt - 1, 20));
        return wait.compareTo(maxBackoff) > 0 ? maxBackoff : wait;
    }

    private static Optional<UpstreamUnavailableException> upstreamFailure(Future<?> check) {
        try {
            check.get();
            return Optional.empty();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UpstreamUnavailableException upstream) {
                return Optional.of(upstream);
            }
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for checks", e);
        }
    }
}
