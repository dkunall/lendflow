package com.lendflow.application.domain;

import com.lendflow.shared.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record LoanApplication(UUID id, String ownerSubject, Product product, LoanTerms terms, Instant consentAt,
                              String consentVersion, ApplicationStatus status, int version, Instant createdAt,
                              Instant submittedAt) {

    public boolean consented() {
        return consentAt != null;
    }

    public LoanApplication withTerms(LoanTerms newTerms) {
        return new LoanApplication(id, ownerSubject, product, newTerms, consentAt, consentVersion, status, version,
            createdAt, submittedAt);
    }

    public LoanApplication withConsent(Instant at, String version) {
        return new LoanApplication(id, ownerSubject, product, terms, at, version, status, this.version, createdAt,
            submittedAt);
    }

    public LoanApplication moveTo(ApplicationStatus next, Instant at) {
        Instant submitted = next == ApplicationStatus.SUBMITTED ? at : submittedAt;
        return new LoanApplication(id, ownerSubject, product, terms, consentAt, consentVersion, next, version,
            createdAt, submitted);
    }
}
