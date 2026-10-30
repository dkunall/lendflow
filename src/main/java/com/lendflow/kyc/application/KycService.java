package com.lendflow.kyc.application;

import com.lendflow.applicant.application.ApplicantService;
import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.kyc.domain.KycAssessment;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Runs once per application. Format and completeness of documents are checked at upload and submit; this is
 * the identity check against the PAN registry.
 */
@Service
public class KycService {

    private final ApplicantService applicants;
    private final PanRegistry registry;
    private final KycRepository repository;
    private final Clock clock;
    private final double passAt;
    private final double referAt;

    KycService(ApplicantService applicants, PanRegistry registry, KycRepository repository, Clock clock,
               @Value("${lendflow.kyc.name-pass:0.85}") double passAt,
               @Value("${lendflow.kyc.name-refer:0.70}") double referAt) {
        this.applicants = applicants;
        this.registry = registry;
        this.repository = repository;
        this.clock = clock;
        this.passAt = passAt;
        this.referAt = referAt;
    }

    /** Called outside any transaction: the registry call can be slow and must not hold a connection. */
    public KycResult check(UUID applicationId) {
        Optional<KycResult> existing = repository.find(applicationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PersonalDetails details = applicants.details(applicationId)
            .orElseThrow(() -> new IllegalStateException("No applicant for " + applicationId));
        KycAssessment assessment = KycAssessment.assess(registry.verify(details.pan(), details.fullName()),
            details.fullName(), passAt, referAt);
        return repository.saveFirst(new KycResult(applicationId, assessment.outcome(), assessment.nameScore(),
            assessment.reasons(), clock.instant()));
    }

    public Optional<KycResult> result(UUID applicationId) {
        return repository.find(applicationId);
    }
}
