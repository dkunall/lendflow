package com.lendflow.application.application;

import com.lendflow.applicant.application.ApplicantService;
import com.lendflow.applicant.application.ApplicantView;
import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.BusinessRuleException;
import com.lendflow.shared.error.InvalidRequestException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.PreconditionFailedException;
import com.lendflow.shared.error.StateConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    /** Shown to the applicant next to the consent checkbox; recorded so we can prove what they agreed to. */
    public static final String CONSENT_VERSION = "bureau-consent-2026-01";

    private final ApplicationRepository repository;
    private final ApplicantService applicants;
    private final ApplicationTransitions transitions;
    private final List<SubmissionCheck> checks;
    private final List<SubmissionListener> listeners;
    private final Clock clock;

    ApplicationService(ApplicationRepository repository, ApplicantService applicants,
                       ApplicationTransitions transitions, List<SubmissionCheck> checks,
                       List<SubmissionListener> listeners, Clock clock) {
        this.repository = repository;
        this.applicants = applicants;
        this.transitions = transitions;
        this.checks = checks;
        this.listeners = listeners;
        this.clock = clock;
    }

    @Transactional
    public ApplicationView create(Actor actor, DraftInput input) {
        if (input.product() == null) {
            throw new InvalidRequestException("product-required", "product is required");
        }
        Instant now = clock.instant();
        LoanApplication draft = new LoanApplication(UUID.randomUUID(), actor.subject(), input.product(),
            input.terms() == null ? LoanTerms.EMPTY : input.terms(), null, null, ApplicationStatus.DRAFT, 1, now, null);
        draft = consent(draft, input.bureauConsent(), now);
        repository.insert(draft);
        repository.appendHistory(new HistoryEntry(draft.id(), null, ApplicationStatus.DRAFT, "CREATE",
            actor.subject(), null, now));
        if (input.applicant() != null) {
            applicants.save(draft.id(), actor.subject(), input.applicant());
        }
        return view(draft);
    }

    @Transactional
    public ApplicationView update(Actor actor, UUID id, int ifMatch, DraftInput input) {
        LoanApplication current = owned(actor, id);
        if (current.version() != ifMatch) {
            throw new PreconditionFailedException("version-mismatch",
                "Application is at version " + current.version() + ", not " + ifMatch);
        }
        if (current.status() != ApplicationStatus.DRAFT) {
            throw new StateConflictException("not-a-draft", "Only drafts can be edited; this one is " + current.status());
        }
        if (input.product() != null && input.product() != current.product()) {
            throw new BusinessRuleException("product-immutable", "Start a new application to change the product");
        }
        LoanApplication updated = current.withTerms(input.terms() == null ? current.terms() : current.terms().merge(input.terms()));
        updated = consent(updated, input.bureauConsent(), clock.instant());
        if (!repository.update(updated, current.version())) {
            throw new PreconditionFailedException("version-mismatch", "Application changed concurrently; reload and retry");
        }
        if (input.applicant() != null) {
            applicants.save(id, actor.subject(), input.applicant());
        }
        return view(repository.find(id).orElseThrow());
    }

    /**
     * Safe to retry: submitting an application that is already past DRAFT returns its current state instead of
     * an error, and only the request that actually moved it queues the workflow.
     */
    @Transactional
    public ApplicationView submit(Actor actor, UUID id) {
        LoanApplication current = owned(actor, id);
        if (current.status() != ApplicationStatus.DRAFT) {
            return view(current);
        }
        List<String> missing = new ArrayList<>(current.terms().missing(current.product().isSecured()));
        PersonalDetails details = applicants.details(id).orElse(null);
        if (details == null || !details.complete()) {
            missing.add("applicant");
        }
        if (!current.consented()) {
            missing.add("bureauConsent");
        }
        for (SubmissionCheck check : checks) {
            missing.addAll(check.missing(current));
        }
        if (!missing.isEmpty()) {
            throw new BusinessRuleException("application-incomplete", "Application is not ready to submit",
                Map.of("missing", missing));
        }
        LoanApplication submitted = transitions.tryApply(current, LifecycleEvent.SUBMIT, actor.subject(), null)
            .orElse(null);
        if (submitted == null) {
            return view(repository.find(id).orElseThrow());
        }
        applicants.markSubmitted(id);
        listeners.forEach(l -> l.submitted(id));
        return view(submitted);
    }

    @Transactional
    public ApplicationView withdraw(Actor actor, UUID id) {
        LoanApplication current = owned(actor, id);
        return view(transitions.apply(current, LifecycleEvent.WITHDRAW, actor.subject(), "withdrawn by applicant"));
    }

    @Transactional(readOnly = true)
    public ApplicationView get(Actor actor, UUID id) {
        return view(readable(actor, id));
    }

    @Transactional(readOnly = true)
    public List<ApplicationView> mine(Actor actor) {
        return repository.listByOwner(actor.subject(), 50).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> history(Actor actor, UUID id) {
        readable(actor, id);
        return repository.history(id);
    }

    /**
     * Applicants see their own applications; staff see all. Anyone else gets 404 rather than 403, so ids
     * cannot be probed.
     */
    @Transactional(readOnly = true)
    public LoanApplication readable(Actor actor, UUID id) {
        LoanApplication application = find(id);
        if (actor.isStaff() || application.ownerSubject().equals(actor.subject())) {
            return application;
        }
        throw notFound(id);
    }

    @Transactional(readOnly = true)
    public LoanApplication owned(Actor actor, UUID id) {
        LoanApplication application = find(id);
        if (!application.ownerSubject().equals(actor.subject())) {
            throw notFound(id);
        }
        return application;
    }

    @Transactional(readOnly = true)
    public LoanApplication find(UUID id) {
        return repository.find(id).orElseThrow(() -> notFound(id));
    }

    private LoanApplication consent(LoanApplication application, Boolean consent, Instant now) {
        if (Boolean.TRUE.equals(consent) && !application.consented()) {
            return application.withConsent(now, CONSENT_VERSION);
        }
        if (Boolean.FALSE.equals(consent)) {
            return application.withConsent(null, null);
        }
        return application;
    }

    private ApplicationView view(LoanApplication application) {
        ApplicantView applicant = applicants.view(application.id()).orElse(null);
        return ApplicationView.of(application, applicant);
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("application-not-found", "No application " + id);
    }
}
