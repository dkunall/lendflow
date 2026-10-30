package com.lendflow.application.application;

import com.lendflow.applicant.application.ApplicantView;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.shared.domain.Product;
import java.time.Instant;
import java.util.UUID;

public record ApplicationView(UUID id, Product product, ApplicationStatus status, int version, LoanTerms terms,
                              boolean bureauConsent, ApplicantView applicant, Instant createdAt,
                              Instant submittedAt) {

    static ApplicationView of(LoanApplication a, ApplicantView applicant) {
        return new ApplicationView(a.id(), a.product(), a.status(), a.version(), a.terms(), a.consented(), applicant,
            a.createdAt(), a.submittedAt());
    }
}
