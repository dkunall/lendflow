package com.lendflow.kyc.adapter.persistence;

import com.lendflow.kyc.application.KycRepository;
import com.lendflow.kyc.application.KycResult;
import com.lendflow.kyc.domain.KycOutcome;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcKycRepository implements KycRepository {

    private final JdbcClient jdbc;

    JdbcKycRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public KycResult saveFirst(KycResult r) {
        jdbc.sql("""
                INSERT INTO kyc_checks (application_id, outcome, name_score, reasons, checked_at)
                VALUES (?, ?, ?, string_to_array(?, ','), ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(r.applicationId(), r.outcome().name(), r.nameScore(), String.join(",", r.reasons()),
                Timestamp.from(r.checkedAt()))
            .update();
        return find(r.applicationId()).orElseThrow();
    }

    @Override
    public Optional<KycResult> find(UUID applicationId) {
        return jdbc.sql("SELECT application_id, outcome, name_score, reasons, checked_at FROM kyc_checks WHERE application_id = ?")
            .param(applicationId)
            .query((rs, row) -> new KycResult(rs.getObject("application_id", UUID.class),
                KycOutcome.valueOf(rs.getString("outcome")), rs.getDouble("name_score"), strings(rs.getArray("reasons")),
                rs.getTimestamp("checked_at").toInstant()))
            .optional();
    }

    private static List<String> strings(Array array) throws java.sql.SQLException {
        return array == null ? List.of() : Arrays.stream((String[]) array.getArray()).filter(s -> !s.isEmpty()).toList();
    }
}
