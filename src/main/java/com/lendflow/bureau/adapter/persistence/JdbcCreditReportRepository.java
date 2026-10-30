package com.lendflow.bureau.adapter.persistence;

import com.lendflow.bureau.application.CreditReportRepository;
import com.lendflow.bureau.domain.CreditReport;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCreditReportRepository implements CreditReportRepository {

    private final JdbcClient jdbc;

    JdbcCreditReportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CreditReport saveFirst(CreditReport r) {
        jdbc.sql("""
                INSERT INTO credit_reports (application_id, bureau_ref, hit, score, active_accounts, total_outstanding,
                    max_dpd_12m, enquiries_6m, months_on_bureau, written_off, reported_emi, consent_version, pulled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(r.applicationId(), r.bureauRef(), r.hit(), r.score(), r.activeAccounts(), r.totalOutstanding(),
                r.maxDpdLast12m(), r.enquiriesLast6m(), r.monthsOnBureau(), r.writtenOff(), r.reportedEmi(),
                r.consentVersion(), Timestamp.from(r.pulledAt()))
            .update();
        return find(r.applicationId()).orElseThrow();
    }

    @Override
    public Optional<CreditReport> find(UUID applicationId) {
        return jdbc.sql("SELECT * FROM credit_reports WHERE application_id = ?")
            .param(applicationId)
            .query((rs, row) -> new CreditReport(rs.getObject("application_id", UUID.class), rs.getString("bureau_ref"),
                rs.getBoolean("hit"), rs.getObject("score", Integer.class), rs.getInt("active_accounts"),
                rs.getBigDecimal("total_outstanding"), rs.getInt("max_dpd_12m"), rs.getInt("enquiries_6m"),
                rs.getInt("months_on_bureau"), rs.getBoolean("written_off"), rs.getBigDecimal("reported_emi"),
                rs.getString("consent_version"), rs.getTimestamp("pulled_at").toInstant()))
            .optional();
    }
}
