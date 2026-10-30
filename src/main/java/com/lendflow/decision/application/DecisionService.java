package com.lendflow.decision.application;

import com.lendflow.applicant.application.ApplicantService;
import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.bureau.application.BureauService;
import com.lendflow.bureau.domain.CreditReport;
import com.lendflow.decision.domain.CreditDecision;
import com.lendflow.decision.domain.CreditFacts;
import com.lendflow.decision.domain.DecisionEngine;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.kyc.application.KycResult;
import com.lendflow.kyc.application.KycService;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.json.Json;
import com.lendflow.shared.messaging.Topics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionService {

    /** Ages are counted on the Indian calendar date, whatever zone the server runs in. */
    static final ZoneId LENDING_ZONE = ZoneId.of("Asia/Kolkata");

    private final ApplicationService applications;
    private final ApplicantService applicants;
    private final KycService kyc;
    private final BureauService bureau;
    private final PolicyCatalog policies;
    private final DecisionRepository repository;
    private final OutboxWriter outbox;
    private final Json json;
    private final Clock clock;
    private final MeterRegistry meters;

    DecisionService(ApplicationService applications, ApplicantService applicants, KycService kyc, BureauService bureau,
                    PolicyCatalog policies, DecisionRepository repository, OutboxWriter outbox, Json json, Clock clock,
                    MeterRegistry meters) {
        this.applications = applications;
        this.applicants = applicants;
        this.kyc = kyc;
        this.bureau = bureau;
        this.policies = policies;
        this.repository = repository;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
        this.meters = meters;
    }

    /**
     * Decides once under the product's published ruleset and records the working. A second call returns the
     * stored decision, even if a newer ruleset has been published since.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public DecisionView decide(UUID applicationId) {
        return repository.find(applicationId).orElseGet(() -> decideNow(applicationId));
    }

    private DecisionView decideNow(UUID applicationId) {
        LoanApplication application = applications.find(applicationId);
        ActivePolicy policy = policies.published(application.product());
        CreditFacts facts = facts(application);
        CreditDecision decision = DecisionEngine.decide(policy.ruleset(), policy.champion(), facts);
        Instant now = clock.instant();
        DecisionView view = new DecisionView(applicationId, policy.stored().id(), decision.ruleset(),
            decision.score().scorecardId(), decision.outcome(), decision.reasons(), decision.score().score(),
            decision.score().grade(), decision.score().probabilityOfDefault(), decision.score().contributions(),
            decision.firedRules(), decision.annualRate(), decision.processingFeePercent(), decision.maxEmi(),
            decision.approvedAmount(), decision.emi(), json.tree(facts), now);
        if (!repository.insertFirst(view)) {
            return repository.find(applicationId).orElseThrow();
        }
        policy.challengerScorecard().ifPresent(challenger -> shadow(view, facts, challenger, now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", applicationId);
        payload.put("ruleset", view.ruleset());
        payload.put("outcome", view.outcome());
        payload.put("reasons", view.reasons());
        payload.put("grade", view.grade());
        payload.put("approvedAmount", view.approvedAmount());
        payload.put("at", now);
        outbox.append(Topics.APPLICATIONS, applicationId.toString(), "CreditDecided", payload);
        meters.counter("lendflow.decisions", "outcome", view.outcome().name(), "ruleset", view.ruleset()).increment();
        return view;
    }

    private void shadow(DecisionView champion, CreditFacts facts, Scorecard challenger, Instant now) {
        Scorecard.Result result = challenger.score(facts.asMap());
        repository.saveShadow(new ShadowScore(champion.applicationId(), champion.ruleset(), champion.scorecard(),
            champion.grade(), champion.score(), result.scorecardId(), result.grade(), result.score(), now));
    }

    CreditFacts facts(LoanApplication application) {
        UUID id = application.id();
        PersonalDetails person = applicants.details(id).orElseThrow(() -> new IllegalStateException("No applicant " + id));
        KycResult kycResult = kyc.result(id).orElseThrow(() -> new IllegalStateException("KYC not done for " + id));
        CreditReport report = bureau.report(id).orElseThrow(() -> new IllegalStateException("No bureau pull for " + id));
        boolean duplicate = !applicants.duplicates(id).isEmpty();
        LoanTerms terms = application.terms();
        LocalDate today = LocalDate.ofInstant(clock.instant(), LENDING_ZONE);
        int age = Period.between(person.dateOfBirth(), today).getYears();
        int ageAtMaturity = Period.between(person.dateOfBirth(), today.plusMonths(terms.tenureMonths())).getYears();
        return new CreditFacts(age, ageAtMaturity, terms.employmentType().name(), terms.monthsInCurrentJob(),
            terms.monthlyIncome(), terms.existingEmi(), terms.amount(), terms.tenureMonths(), terms.propertyValue(),
            kycResult.outcome().name(), report.hit(), report.score(), report.maxDpdLast12m(), report.enquiriesLast6m(),
            report.monthsOnBureau(), report.writtenOff(), report.reportedEmi(),
            duplicate);
    }

    @Transactional(readOnly = true)
    public Object view(Actor actor, UUID applicationId) {
        applications.readable(actor, applicationId);
        DecisionView view = find(applicationId);
        return actor.isStaff() ? view : view.forApplicant();
    }

    @Transactional(readOnly = true)
    public Optional<DecisionView> recorded(UUID applicationId) {
        return repository.find(applicationId);
    }

    @Transactional(readOnly = true)
    public DecisionView find(UUID applicationId) {
        return repository.find(applicationId)
            .orElseThrow(() -> new NotFoundException("decision-not-found", "No decision yet for " + applicationId));
    }

    @Transactional(readOnly = true)
    public ShadowReport shadowReport(String rulesetKey) {
        List<ShadowReport.Cell> cells = repository.shadowCells(rulesetKey);
        long total = cells.stream().mapToLong(ShadowReport.Cell::count).sum();
        long same = cells.stream().filter(c -> c.championGrade().equals(c.challengerGrade()))
            .mapToLong(ShadowReport.Cell::count).sum();
        String champion = cells.isEmpty() ? null : cells.getFirst().championId();
        String challenger = cells.isEmpty() ? null : cells.getFirst().challengerId();
        return new ShadowReport(rulesetKey, champion, challenger, total, same, cells);
    }
}
