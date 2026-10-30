package com.lendflow.bureau.application;

import com.lendflow.applicant.application.ApplicantService;
import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.bureau.domain.CreditReport;
import com.lendflow.shared.error.BusinessRuleException;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * One pull per application. Every pull is a hard enquiry on the applicant's file, so a retry or a second
 * decision run must reuse the stored report rather than pull again.
 */
@Service
public class BureauService {

    private final ApplicationService applications;
    private final ApplicantService applicants;
    private final BureauClient client;
    private final CreditReportRepository reports;
    private final Clock clock;

    BureauService(ApplicationService applications, ApplicantService applicants, BureauClient client,
                  CreditReportRepository reports, Clock clock) {
        this.applications = applications;
        this.applicants = applicants;
        this.client = client;
        this.reports = reports;
        this.clock = clock;
    }

    /** Outside any transaction, for the same reason as the KYC check. */
    public CreditReport pull(UUID applicationId) {
        Optional<CreditReport> existing = reports.find(applicationId);
        if (existing.isPresent()) {
            return existing.get();
        }
        LoanApplication application = applications.find(applicationId);
        if (!application.consented()) {
            throw new BusinessRuleException("consent-missing", "No bureau consent recorded for " + applicationId);
        }
        PersonalDetails details = applicants.details(applicationId)
            .orElseThrow(() -> new IllegalStateException("No applicant for " + applicationId));
        BureauResponse r = client.fetch(new BureauRequest(details.pan(), details.fullName(), details.dateOfBirth(),
            application.consentVersion(), application.consentAt()));
        CreditReport report = r.hit()
            ? new CreditReport(applicationId, r.bureauRef(), true, r.score(), orZero(r.activeAccounts()),
                orZero(r.totalOutstanding()), orZero(r.maxDpdLast12m()), orZero(r.enquiriesLast6m()),
                orZero(r.monthsOnBureau()), Boolean.TRUE.equals(r.writtenOff()), orZero(r.reportedEmi()),
                application.consentVersion(), clock.instant())
            : CreditReport.noHit(applicationId, r.bureauRef(), application.consentVersion(), clock.instant());
        return reports.saveFirst(report);
    }

    public Optional<CreditReport> report(UUID applicationId) {
        return reports.find(applicationId);
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
