package com.lendflow.application.adapter.persistence;

import com.lendflow.application.application.ApplicationRepository;
import com.lendflow.application.application.HistoryEntry;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.EmploymentType;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.shared.domain.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApplicationRepository implements ApplicationRepository {

    private static final String COLUMNS = """
        id, owner_subject, product, amount, tenure_months, monthly_income, existing_emi, employment_type,
        months_in_job, property_value, consent_at, consent_version, status, version, created_at, submitted_at""";

    private final JdbcClient jdbc;

    JdbcApplicationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(LoanApplication a) {
        LoanTerms t = a.terms();
        jdbc.sql("""
                INSERT INTO applications (id, owner_subject, product, amount, tenure_months, monthly_income,
                    existing_emi, employment_type, months_in_job, property_value, consent_at, consent_version,
                    status, version, created_at, updated_at)
                VALUES (:id, :owner, :product, :amount, :tenure, :income, :emi, :employment, :months, :property,
                    :consentAt, :consentVersion, :status, :version, :createdAt, :createdAt)
                """)
            .param("id", a.id())
            .param("owner", a.ownerSubject())
            .param("product", a.product().name())
            .param("amount", t.amount())
            .param("tenure", t.tenureMonths())
            .param("income", t.monthlyIncome())
            .param("emi", t.existingEmi())
            .param("employment", t.employmentType() == null ? null : t.employmentType().name())
            .param("months", t.monthsInCurrentJob())
            .param("property", t.propertyValue())
            .param("consentAt", timestamp(a.consentAt()))
            .param("consentVersion", a.consentVersion())
            .param("status", a.status().name())
            .param("version", a.version())
            .param("createdAt", timestamp(a.createdAt()))
            .update();
    }

    @Override
    public Optional<LoanApplication> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM applications WHERE id = ?")
            .param(id)
            .query(JdbcApplicationRepository::map)
            .optional();
    }

    @Override
    public boolean update(LoanApplication a, int expectedVersion) {
        LoanTerms t = a.terms();
        return jdbc.sql("""
                UPDATE applications
                   SET amount = :amount, tenure_months = :tenure, monthly_income = :income, existing_emi = :emi,
                       employment_type = :employment, months_in_job = :months, property_value = :property,
                       consent_at = :consentAt, consent_version = :consentVersion, status = :status,
                       submitted_at = :submittedAt, version = version + 1, updated_at = now()
                 WHERE id = :id AND version = :expected
                """)
            .param("id", a.id())
            .param("expected", expectedVersion)
            .param("amount", t.amount())
            .param("tenure", t.tenureMonths())
            .param("income", t.monthlyIncome())
            .param("emi", t.existingEmi())
            .param("employment", t.employmentType() == null ? null : t.employmentType().name())
            .param("months", t.monthsInCurrentJob())
            .param("property", t.propertyValue())
            .param("consentAt", timestamp(a.consentAt()))
            .param("consentVersion", a.consentVersion())
            .param("status", a.status().name())
            .param("submittedAt", timestamp(a.submittedAt()))
            .update() == 1;
    }

    @Override
    public List<LoanApplication> listByOwner(String ownerSubject, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM applications WHERE owner_subject = ? ORDER BY created_at DESC LIMIT ?")
            .params(ownerSubject, limit)
            .query(JdbcApplicationRepository::map)
            .list();
    }

    @Override
    public void appendHistory(HistoryEntry e) {
        jdbc.sql("""
                INSERT INTO application_history (application_id, from_status, to_status, event, actor, reason, at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
            .params(e.applicationId(), e.from() == null ? null : e.from().name(), e.to().name(), e.event(), e.actor(),
                e.reason(), Timestamp.from(e.at()))
            .update();
    }

    @Override
    public List<HistoryEntry> history(UUID applicationId) {
        return jdbc.sql("""
                SELECT application_id, from_status, to_status, event, actor, reason, at
                  FROM application_history WHERE application_id = ? ORDER BY id
                """)
            .param(applicationId)
            .query((rs, row) -> new HistoryEntry(rs.getObject("application_id", UUID.class),
                rs.getString("from_status") == null ? null : ApplicationStatus.valueOf(rs.getString("from_status")),
                ApplicationStatus.valueOf(rs.getString("to_status")), rs.getString("event"), rs.getString("actor"),
                rs.getString("reason"), rs.getTimestamp("at").toInstant()))
            .list();
    }

    private static LoanApplication map(ResultSet rs, int row) throws SQLException {
        String employment = rs.getString("employment_type");
        LoanTerms terms = new LoanTerms(rs.getBigDecimal("amount"), (Integer) rs.getObject("tenure_months"),
            rs.getBigDecimal("monthly_income"), rs.getBigDecimal("existing_emi"),
            employment == null ? null : EmploymentType.valueOf(employment), (Integer) rs.getObject("months_in_job"),
            rs.getBigDecimal("property_value"));
        return new LoanApplication(rs.getObject("id", UUID.class), rs.getString("owner_subject"),
            Product.valueOf(rs.getString("product")), terms, instant(rs.getTimestamp("consent_at")),
            rs.getString("consent_version"), ApplicationStatus.valueOf(rs.getString("status")), rs.getInt("version"),
            instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("submitted_at")));
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
