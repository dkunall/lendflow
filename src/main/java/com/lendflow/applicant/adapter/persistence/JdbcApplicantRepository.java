package com.lendflow.applicant.adapter.persistence;

import com.lendflow.applicant.application.ApplicantRecord;
import com.lendflow.applicant.application.ApplicantRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcApplicantRepository implements ApplicantRepository {

    private static final String COLUMNS = """
        application_id, owner_subject, full_name, comparable_name, date_of_birth, pan_hmac, pan_enc,
        phone_hmac, phone_enc, email_hmac, email_enc, submitted_at""";

    private final JdbcClient jdbc;

    JdbcApplicantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void upsert(ApplicantRecord a) {
        jdbc.sql("""
                INSERT INTO applicants (application_id, owner_subject, full_name, comparable_name, date_of_birth,
                                        pan_hmac, pan_enc, phone_hmac, phone_enc, email_hmac, email_enc)
                VALUES (:id, :owner, :name, :comparable, :dob, :panHmac, :panEnc, :phoneHmac, :phoneEnc,
                        :emailHmac, :emailEnc)
                ON CONFLICT (application_id) DO UPDATE SET
                    full_name = EXCLUDED.full_name, comparable_name = EXCLUDED.comparable_name,
                    date_of_birth = EXCLUDED.date_of_birth, pan_hmac = EXCLUDED.pan_hmac, pan_enc = EXCLUDED.pan_enc,
                    phone_hmac = EXCLUDED.phone_hmac, phone_enc = EXCLUDED.phone_enc,
                    email_hmac = EXCLUDED.email_hmac, email_enc = EXCLUDED.email_enc, updated_at = now()
                """)
            .param("id", a.applicationId())
            .param("owner", a.ownerSubject())
            .param("name", a.fullName())
            .param("comparable", a.comparableName())
            .param("dob", a.dateOfBirth())
            .param("panHmac", a.panHmac())
            .param("panEnc", a.panEnc())
            .param("phoneHmac", a.phoneHmac())
            .param("phoneEnc", a.phoneEnc())
            .param("emailHmac", a.emailHmac())
            .param("emailEnc", a.emailEnc())
            .update();
    }

    @Override
    public Optional<ApplicantRecord> find(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM applicants WHERE application_id = ?")
            .param(applicationId)
            .query(JdbcApplicantRepository::map)
            .optional();
    }

    @Override
    public void markSubmitted(UUID applicationId, Instant at) {
        jdbc.sql("UPDATE applicants SET submitted_at = ? WHERE application_id = ?")
            .params(Timestamp.from(at), applicationId)
            .update();
    }

    @Override
    public List<ApplicantRecord> candidates(UUID exclude, String panHmac, String phoneHmac, String emailHmac,
                                            LocalDate dateOfBirth, Instant since) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM applicants
                WHERE application_id <> :self AND submitted_at >= :since
                  AND (pan_hmac = :pan OR phone_hmac = :phone OR email_hmac = :email OR date_of_birth = :dob)
                ORDER BY submitted_at DESC
                LIMIT 200
                """)
            .param("self", exclude)
            .param("since", Timestamp.from(since))
            .param("pan", panHmac)
            .param("phone", phoneHmac)
            .param("email", emailHmac)
            .param("dob", dateOfBirth)
            .query(JdbcApplicantRepository::map)
            .list();
    }

    private static ApplicantRecord map(ResultSet rs, int row) throws SQLException {
        Timestamp submitted = rs.getTimestamp("submitted_at");
        return new ApplicantRecord(rs.getObject("application_id", UUID.class), rs.getString("owner_subject"),
            rs.getString("full_name"), rs.getString("comparable_name"), rs.getObject("date_of_birth", LocalDate.class),
            rs.getString("pan_hmac"), rs.getString("pan_enc"), rs.getString("phone_hmac"), rs.getString("phone_enc"),
            rs.getString("email_hmac"), rs.getString("email_enc"), submitted == null ? null : submitted.toInstant());
    }
}
