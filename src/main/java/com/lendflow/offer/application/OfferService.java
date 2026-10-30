package com.lendflow.offer.application;

import com.lendflow.applicant.application.ApplicantService;
import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.application.ApplicationTransitions;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.decision.application.DecisionService;
import com.lendflow.decision.application.DecisionView;
import com.lendflow.idempotency.application.Idempotency;
import com.lendflow.offer.domain.Amortization;
import com.lendflow.offer.domain.Instalment;
import com.lendflow.offer.domain.Offer;
import com.lendflow.offer.domain.OfferStatus;
import com.lendflow.offer.domain.OfferTerms;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.BusinessRuleException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import com.lendflow.shared.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferService {

    private final OfferRepository offers;
    private final OfferPricing pricing;
    private final ApplicationService applications;
    private final ApplicationTransitions transitions;
    private final ApplicantService applicants;
    private final DecisionService decisions;
    private final Idempotency idempotency;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meters;

    OfferService(OfferRepository offers, OfferPricing pricing, ApplicationService applications,
                 ApplicationTransitions transitions, ApplicantService applicants, DecisionService decisions,
                 Idempotency idempotency, OutboxWriter outbox, Clock clock, MeterRegistry meters) {
        this.offers = offers;
        this.pricing = pricing;
        this.applications = applications;
        this.transitions = transitions;
        this.applicants = applicants;
        this.decisions = decisions;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
    }

    /** Prices the decision's terms for an approved application. Safe to call again: the first offer stands. */
    @Transactional(propagation = Propagation.MANDATORY)
    public OfferView create(UUID applicationId) {
        LoanApplication application = applications.find(applicationId);
        DecisionView decision = decisions.find(applicationId);
        if (decision.approvedAmount() == null || decision.annualRate() == null) {
            throw new BusinessRuleException("no-approved-terms", "Decision for " + applicationId + " has no terms to offer");
        }
        OfferTerms terms = new OfferTerms(application.product(), decision.approvedAmount(), decision.annualRate(),
            application.terms().tenureMonths(), decision.processingFeePercent());
        Instant now = clock.instant();
        Offer offer = pricing.forProduct(application.product()).generate(UUID.randomUUID(), applicationId, terms, now);
        if (!offers.insertFirst(offer)) {
            return OfferView.of(offers.forApplication(applicationId).orElseThrow(), now);
        }
        outbox.append(Topics.APPLICATIONS, applicationId.toString(), "OfferMade", Map.of(
            "applicationId", applicationId, "offerId", offer.id(), "amount", offer.amount(), "emi", offer.emi(),
            "validUntil", offer.validUntil()));
        return OfferView.of(offer, now);
    }

    @Transactional(readOnly = true)
    public OfferView get(Actor actor, UUID offerId) {
        return OfferView.of(readable(actor, offerId), clock.instant());
    }

    @Transactional(readOnly = true)
    public OfferView forApplication(Actor actor, UUID applicationId) {
        applications.readable(actor, applicationId);
        return offers.forApplication(applicationId).map(o -> OfferView.of(o, clock.instant()))
            .orElseThrow(() -> new NotFoundException("offer-not-found", "No offer for application " + applicationId));
    }

    @Transactional(readOnly = true)
    public Schedule schedule(Actor actor, UUID offerId) {
        Offer offer = readable(actor, offerId);
        List<Instalment> rows = Amortization.schedule(offer.amount(), offer.annualRate(), offer.tenureMonths());
        return new Schedule(offer.id(), offer.amount(), offer.annualRate(), offer.emi(), offer.totalInterest(), rows);
    }

    /**
     * Accepting sanctions the loan. The key makes a retried click return the same result; the row lock makes
     * two different keys racing on one offer settle as one acceptance and one conflict.
     */
    @Transactional
    public OfferView accept(Actor actor, UUID offerId, String idempotencyKey) {
        return idempotency.once("accept:" + actor.subject(), idempotencyKey, Map.of("offerId", offerId),
            OfferView.class, () -> acceptOnce(actor, offerId));
    }

    private OfferView acceptOnce(Actor actor, UUID offerId) {
        Offer offer = offers.lock(offerId).orElseThrow(() -> notFound(offerId));
        LoanApplication application = applications.owned(actor, offer.applicationId());
        Instant now = clock.instant();
        switch (offer.statusAt(now)) {
            case ACCEPTED -> throw new StateConflictException("offer-already-accepted", "Offer " + offerId + " was already accepted");
            case EXPIRED -> throw new StateConflictException("offer-expired", "Offer " + offerId + " expired at " + offer.validUntil());
            case OFFERED -> { }
        }
        if (application.status() != ApplicationStatus.APPROVED) {
            throw new StateConflictException("application-not-approved",
                "Application " + application.id() + " is " + application.status());
        }
        Offer accepted = offer.accepted(now, actor.subject());
        offers.markAccepted(accepted);
        transitions.apply(application, LifecycleEvent.OFFER_ACCEPTED, actor.subject(), null);
        outbox.append(Topics.SANCTIONS, application.id().toString(), "LoanSanctioned", sanctioned(accepted));
        meters.counter("lendflow.offers.accepted", "product", offer.product().name()).increment();
        return OfferView.of(accepted, now);
    }

    @Transactional(readOnly = true)
    public String sanctionLetter(Actor actor, UUID offerId) {
        Offer offer = readable(actor, offerId);
        if (offer.status() != OfferStatus.ACCEPTED) {
            throw new StateConflictException("offer-not-accepted", "A sanction letter exists only for an accepted offer");
        }
        String name = applicants.details(offer.applicationId()).map(PersonalDetails::fullName).orElse("Applicant");
        return SanctionLetter.render(offer, name);
    }

    private Offer readable(Actor actor, UUID offerId) {
        Offer offer = offers.find(offerId).orElseThrow(() -> notFound(offerId));
        applications.readable(actor, offer.applicationId());
        return offer;
    }

    private static NotFoundException notFound(UUID offerId) {
        return new NotFoundException("offer-not-found", "No offer " + offerId);
    }

    private static Map<String, Object> sanctioned(Offer o) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", o.applicationId());
        payload.put("offerId", o.id());
        payload.put("product", o.product());
        payload.put("amount", o.amount());
        payload.put("netDisbursal", o.netDisbursal());
        payload.put("annualRate", o.annualRate());
        payload.put("tenureMonths", o.tenureMonths());
        payload.put("emi", o.emi());
        payload.put("sanctionedAt", o.acceptedAt());
        return payload;
    }
}
