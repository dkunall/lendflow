package com.lendflow.disbursement.application;

import com.lendflow.application.application.ApplicationTransitions;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.disbursement.domain.Disbursement;
import com.lendflow.disbursement.domain.DisbursementStatus;
import com.lendflow.disbursement.domain.Utr;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.outbox.application.ProcessedEvents;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.InvalidRequestException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import com.lendflow.shared.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisbursementService {

    static final String CONSUMER = "disbursement";
    private static final Logger log = LoggerFactory.getLogger(DisbursementService.class);

    private final DisbursementRepository disbursements;
    private final ProcessedEvents processed;
    private final ApplicationTransitions transitions;
    private final OutboxWriter outbox;
    private final Clock clock;
    private final MeterRegistry meters;

    DisbursementService(DisbursementRepository disbursements, ProcessedEvents processed,
                        ApplicationTransitions transitions, OutboxWriter outbox, Clock clock, MeterRegistry meters) {
        this.disbursements = disbursements;
        this.processed = processed;
        this.transitions = transitions;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
    }

    /**
     * Turns a sanction into a payout request. The event id claim and the insert share a transaction, so a
     * redelivered event changes nothing; the unique application id covers a sanction published twice under
     * different event ids.
     */
    @Transactional
    public void onSanctioned(UUID eventId, SanctionedLoan loan) {
        if (!processed.firstTime(CONSUMER, eventId)) {
            return;
        }
        Disbursement request = Disbursement.requested(UUID.randomUUID(), loan.applicationId(), loan.offerId(),
            loan.product(), loan.netDisbursal(), clock.instant());
        if (disbursements.insertFirst(request)) {
            meters.counter("lendflow.disbursements.requested", "product", loan.product()).increment();
        } else {
            log.info("Application {} already has a disbursement request; event {} ignored", loan.applicationId(), eventId);
        }
    }

    @Transactional(readOnly = true)
    public List<Disbursement> pending(int limit) {
        return disbursements.withStatus(DisbursementStatus.REQUESTED, Math.clamp(limit, 1, 200));
    }

    @Transactional(readOnly = true)
    public Disbursement get(UUID id) {
        return disbursements.find(id).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public Optional<Disbursement> forApplication(UUID applicationId) {
        return disbursements.forApplication(applicationId);
    }

    /**
     * Ops records the bank reference once the transfer has gone out. Repeating the same reference is a no-op,
     * so a retried request is safe; a different reference for a paid loan is a conflict someone must look at.
     */
    @Transactional
    public Disbursement confirm(Actor actor, UUID id, String rawUtr) {
        Utr utr = Utr.parse(rawUtr).orElseThrow(() ->
            new InvalidRequestException("utr-invalid", "UTR must be 12 to 22 letters or digits"));
        Disbursement current = disbursements.lock(id).orElseThrow(() -> notFound(id));
        if (current.status() == DisbursementStatus.SENT) {
            if (current.utr().equals(utr.value())) {
                return current;
            }
            throw new StateConflictException("already-disbursed", "Disbursement " + id + " was confirmed with another UTR");
        }
        if (disbursements.utrTaken(utr.value())) {
            throw new StateConflictException("utr-already-used", "That UTR is recorded against another disbursement");
        }
        Instant now = clock.instant();
        Disbursement sent = current.sent(utr, actor.subject(), now);
        disbursements.markSent(sent);
        transitions.apply(sent.applicationId(), LifecycleEvent.FUNDS_SENT, actor.subject(), "UTR " + utr.value());
        outbox.append(Topics.APPLICATIONS, sent.applicationId().toString(), "LoanDisbursed", Map.of(
            "applicationId", sent.applicationId(), "disbursementId", sent.id(), "amount", sent.amount(),
            "utr", sent.utr(), "sentAt", now.toString()));
        meters.counter("lendflow.disbursements.sent", "product", sent.product()).increment();
        return sent;
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("disbursement-not-found", "No disbursement " + id);
    }
}
