package com.lendflow.application.domain;

import static com.lendflow.application.domain.ApplicationStatus.APPROVED;
import static com.lendflow.application.domain.ApplicationStatus.DISBURSED;
import static com.lendflow.application.domain.ApplicationStatus.DRAFT;
import static com.lendflow.application.domain.ApplicationStatus.REFER;
import static com.lendflow.application.domain.ApplicationStatus.REJECTED;
import static com.lendflow.application.domain.ApplicationStatus.SANCTIONED;
import static com.lendflow.application.domain.ApplicationStatus.SUBMITTED;
import static com.lendflow.application.domain.ApplicationStatus.UNDERWRITING;
import static com.lendflow.application.domain.ApplicationStatus.WITHDRAWN;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The whole lifecycle as one transition table. Anything not listed is illegal, so a new status cannot be
 * reached by accident from code that forgot to check.
 */
public final class ApplicationLifecycle {

    private static final Map<ApplicationStatus, Map<LifecycleEvent, ApplicationStatus>> TABLE =
        new EnumMap<>(ApplicationStatus.class);

    static {
        allow(DRAFT, LifecycleEvent.SUBMIT, SUBMITTED);
        allow(DRAFT, LifecycleEvent.WITHDRAW, WITHDRAWN);
        allow(SUBMITTED, LifecycleEvent.CHECKS_COMPLETED, UNDERWRITING);
        allow(UNDERWRITING, LifecycleEvent.AUTO_APPROVE, APPROVED);
        allow(UNDERWRITING, LifecycleEvent.REFER, REFER);
        allow(UNDERWRITING, LifecycleEvent.AUTO_REJECT, REJECTED);
        allow(REFER, LifecycleEvent.CHECKER_APPROVE, APPROVED);
        allow(REFER, LifecycleEvent.MANUAL_DECLINE, REJECTED);
        allow(APPROVED, LifecycleEvent.OFFER_ACCEPTED, SANCTIONED);
        allow(APPROVED, LifecycleEvent.WITHDRAW, WITHDRAWN);
        allow(SANCTIONED, LifecycleEvent.FUNDS_SENT, DISBURSED);
    }

    private ApplicationLifecycle() {
    }

    public static Optional<ApplicationStatus> next(ApplicationStatus from, LifecycleEvent event) {
        return Optional.ofNullable(TABLE.getOrDefault(from, Map.of()).get(event));
    }

    public static ApplicationStatus require(ApplicationStatus from, LifecycleEvent event) {
        return next(from, event).orElseThrow(() -> new IllegalTransitionException(from, event));
    }

    private static void allow(ApplicationStatus from, LifecycleEvent event, ApplicationStatus to) {
        TABLE.computeIfAbsent(from, k -> new EnumMap<>(LifecycleEvent.class)).put(event, to);
    }
}
