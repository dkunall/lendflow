package com.lendflow.disbursement.adapter.persistence;

import com.lendflow.disbursement.application.DisbursementRepository;
import com.lendflow.disbursement.domain.Disbursement;
import com.lendflow.disbursement.domain.DisbursementStatus;
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
class JdbcDisbursementRepository implements DisbursementRepository {

    private static final String COLUMNS =
        "id, application_id, offer_id, product, amount, status, utr, requested_at, sent_at, sent_by";

    private final JdbcClient jdbc;

    JdbcDisbursementRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertFirst(Disbursement d) {
        return jdbc.sql("""
                INSERT INTO disbursements (id, application_id, offer_id, product, amount, status, requested_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(d.id(), d.applicationId(), d.offerId(), d.product(), d.amount(), d.status().name(),
                Timestamp.from(d.requestedAt()))
            .update() == 1;
    }

    @Override
    public Optional<Disbursement> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disbursements WHERE id = ?").param(id).query(this::map).optional();
    }

    @Override
    public Optional<Disbursement> lock(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disbursements WHERE id = ? FOR UPDATE")
            .param(id).query(this::map).optional();
    }

    @Override
    public Optional<Disbursement> forApplication(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disbursements WHERE application_id = ?")
            .param(applicationId).query(this::map).optional();
    }

    @Override
    public boolean utrTaken(String utr) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM disbursements WHERE utr = ?)")
            .param(utr).query(Boolean.class).single();
    }

    @Override
    public void markSent(Disbursement d) {
        int updated = jdbc.sql("""
                UPDATE disbursements SET status = 'SENT', utr = ?, sent_at = ?, sent_by = ?
                WHERE id = ? AND status = 'REQUESTED'
                """)
            .params(d.utr(), Timestamp.from(d.sentAt()), d.sentBy(), d.id())
            .update();
        if (updated != 1) {
            throw new IllegalStateException("Disbursement " + d.id() + " was not pending");
        }
    }

    @Override
    public List<Disbursement> withStatus(DisbursementStatus status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM disbursements WHERE status = ? ORDER BY requested_at LIMIT ?")
            .params(status.name(), limit).query(this::map).list();
    }

    private Disbursement map(ResultSet rs, int row) throws SQLException {
        return new Disbursement(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
            rs.getObject("offer_id", UUID.class), rs.getString("product"), rs.getBigDecimal("amount"),
            DisbursementStatus.valueOf(rs.getString("status")), rs.getString("utr"),
            instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("sent_at")), rs.getString("sent_by"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
