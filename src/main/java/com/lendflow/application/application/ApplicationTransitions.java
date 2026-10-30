package com.lendflow.application.application;

import com.lendflow.application.domain.ApplicationLifecycle;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import com.lendflow.shared.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way any module changes an application's status. Each change is checked against the lifecycle
 * table, written with an optimistic version check, recorded in the history and published through the outbox,
 * all in the caller's transaction.
 */
@Component
public class ApplicationTransitions {

    private final ApplicationRepository repository;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meters;

    ApplicationTransitions(ApplicationRepository repository, OutboxWriter outbox, Clock clock, MeterRegistry meters) {
        this.repository = repository;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LoanApplication apply(UUID applicationId, LifecycleEvent event, String actor, String reason) {
        LoanApplication current = repository.find(applicationId)
            .orElseThrow(() -> new NotFoundException("application-not-found", "No application " + applicationId));
        return apply(current, event, actor, reason);
    }

    /**
     * Applies the event to the copy the caller already read. Losing the version race means someone else moved
     * the application first; the caller gets a conflict rather than a transition from a state that is gone.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LoanApplication apply(LoanApplication current, LifecycleEvent event, String actor, String reason) {
        return tryApply(current, event, actor, reason).orElseThrow(() -> new StateConflictException("concurrent-update",
            "Application " + current.id() + " changed while this request was in flight; reload and retry"));
    }

    /**
     * Like {@link #apply(LoanApplication, LifecycleEvent, String, String)} but reports a lost race as empty
     * instead of throwing, for callers that treat it as "someone else already did it". Throwing through the
     * transactional proxy would mark the caller's transaction rollback-only even if it caught the exception.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LoanApplication> tryApply(LoanApplication current, LifecycleEvent event, String actor,
                                              String reason) {
        ApplicationStatus next = ApplicationLifecycle.require(current.status(), event);
        Instant now = clock.instant();
        LoanApplication moved = current.moveTo(next, now);
        if (!repository.update(moved, current.version())) {
            return Optional.empty();
        }
        repository.appendHistory(new HistoryEntry(current.id(), current.status(), next, event.name(), actor, reason, now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", current.id());
        payload.put("product", current.product());
        payload.put("from", current.status());
        payload.put("to", next);
        payload.put("event", event);
        payload.put("reason", reason);
        payload.put("at", now);
        outbox.append(Topics.APPLICATIONS, current.id().toString(), "ApplicationStatusChanged", payload);
        meters.counter("lendflow.applications.transitions", "to", next.name()).increment();
        return Optional.of(new LoanApplication(moved.id(), moved.ownerSubject(), moved.product(), moved.terms(),
            moved.consentAt(), moved.consentVersion(), moved.status(), current.version() + 1, moved.createdAt(),
            moved.submittedAt()));
    }
}
