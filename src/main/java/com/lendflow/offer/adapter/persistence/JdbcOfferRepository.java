package com.lendflow.offer.adapter.persistence;

import com.lendflow.offer.application.OfferRepository;
import com.lendflow.offer.domain.Offer;
import com.lendflow.offer.domain.OfferStatus;
import com.lendflow.shared.domain.Product;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOfferRepository implements OfferRepository {

    private static final String COLUMNS = """
        id, application_id, product, amount, annual_rate, tenure_months, emi, processing_fee, fee_gst,
        upfront_charges, net_disbursal, total_interest, total_repayable, status, valid_until, created_at,
        accepted_at, accepted_by
        """;

    private final JdbcClient jdbc;

    JdbcOfferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertFirst(Offer o) {
        return jdbc.sql("INSERT INTO offers (" + COLUMNS + """
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(o.id(), o.applicationId(), o.product().name(), o.amount(), o.annualRate(), o.tenureMonths(),
                o.emi(), o.processingFee(), o.feeGst(), o.upfrontCharges(), o.netDisbursal(), o.totalInterest(),
                o.totalRepayable(), o.status().name(), Timestamp.from(o.validUntil()), Timestamp.from(o.createdAt()))
            .update() == 1;
    }

    @Override
    public Optional<Offer> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM offers WHERE id = ?").param(id).query(this::map).optional();
    }

    @Override
    public Optional<Offer> forApplication(UUID applicationId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM offers WHERE application_id = ?")
            .param(applicationId).query(this::map).optional();
    }

    @Override
    public Optional<Offer> lock(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM offers WHERE id = ? FOR UPDATE")
            .param(id).query(this::map).optional();
    }

    @Override
    public void markAccepted(Offer o) {
        int updated = jdbc.sql("""
                UPDATE offers SET status = 'ACCEPTED', accepted_at = ?, accepted_by = ?
                WHERE id = ? AND status = 'OFFERED'
                """)
            .params(Timestamp.from(o.acceptedAt()), o.acceptedBy(), o.id())
            .update();
        if (updated != 1) {
            throw new IllegalStateException("Offer " + o.id() + " was not open for acceptance");
        }
    }

    private Offer map(ResultSet rs, int row) throws SQLException {
        return new Offer(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
            Product.valueOf(rs.getString("product")), rs.getBigDecimal("amount"), rs.getBigDecimal("annual_rate"),
            rs.getInt("tenure_months"), rs.getBigDecimal("emi"), rs.getBigDecimal("processing_fee"),
            rs.getBigDecimal("fee_gst"), rs.getBigDecimal("upfront_charges"), rs.getBigDecimal("net_disbursal"),
            rs.getBigDecimal("total_interest"), rs.getBigDecimal("total_repayable"),
            OfferStatus.valueOf(rs.getString("status")), instant(rs, "valid_until"), instant(rs, "created_at"),
            instant(rs, "accepted_at"), rs.getString("accepted_by"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
