package com.lendflow.idempotency.adapter.persistence;

import com.lendflow.idempotency.application.IdempotencyStore;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyStore implements IdempotencyStore {

    private final JdbcClient jdbc;

    JdbcIdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Stored> claim(String scope, String key, String requestHash) {
        int inserted = jdbc.sql("""
                INSERT INTO idempotency_keys (scope, idem_key, request_hash) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """)
            .params(scope, key, requestHash)
            .update();
        if (inserted == 1) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT request_hash, response::text FROM idempotency_keys WHERE scope = ? AND idem_key = ?")
            .params(scope, key)
            .query((rs, n) -> new Stored(rs.getString(1), rs.getString(2)))
            .optional();
    }

    @Override
    public void complete(String scope, String key, String responseJson) {
        jdbc.sql("UPDATE idempotency_keys SET response = CAST(? AS jsonb) WHERE scope = ? AND idem_key = ?")
            .params(responseJson, scope, key)
            .update();
    }
}
