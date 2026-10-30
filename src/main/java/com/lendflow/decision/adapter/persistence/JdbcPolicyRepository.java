package com.lendflow.decision.adapter.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.decision.application.PolicyRepository;
import com.lendflow.decision.application.RulesetStatus;
import com.lendflow.decision.application.StoredRuleset;
import com.lendflow.shared.domain.Product;
import com.lendflow.shared.json.Json;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPolicyRepository implements PolicyRepository {

    private final JdbcClient jdbc;
    private final Json json;

    JdbcPolicyRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean addScorecard(String id, JsonNode definition) {
        return jdbc.sql("INSERT INTO scorecards (id, definition) VALUES (?, CAST(? AS jsonb)) ON CONFLICT DO NOTHING")
            .params(id, json.write(definition))
            .update() == 1;
    }

    @Override
    public Optional<JsonNode> scorecard(String id) {
        return jdbc.sql("SELECT definition::text FROM scorecards WHERE id = ?")
            .param(id)
            .query(String.class)
            .optional()
            .map(json::read);
    }

    @Override
    public boolean seedPublished(UUID id, Product product, int version, JsonNode definition) {
        return jdbc.sql("""
                INSERT INTO rulesets (id, product, version, status, definition, created_by, published_by, published_at)
                VALUES (?, ?, ?, 'PUBLISHED', CAST(? AS jsonb), 'seed', 'seed', now())
                ON CONFLICT DO NOTHING
                """)
            .params(id, product.name(), version, json.write(definition))
            .update() == 1;
    }

    @Override
    public void insertDraft(StoredRuleset d) {
        jdbc.sql("""
                INSERT INTO rulesets (id, product, version, status, definition, created_by, created_at)
                VALUES (?, ?, ?, 'DRAFT', CAST(? AS jsonb), ?, ?)
                """)
            .params(d.id(), d.product().name(), d.version(), json.write(d.definition()), d.createdBy(),
                Timestamp.from(d.createdAt()))
            .update();
    }

    @Override
    public Optional<StoredRuleset> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = ?").param(id).query(this::map).optional();
    }

    @Override
    public Optional<StoredRuleset> published(Product product) {
        return jdbc.sql(SELECT + " WHERE product = ? AND status = 'PUBLISHED'").param(product.name()).query(this::map)
            .optional();
    }

    @Override
    public List<StoredRuleset> all(Product product) {
        return jdbc.sql(SELECT + " WHERE product = ? ORDER BY version").param(product.name()).query(this::map).list();
    }

    @Override
    public int nextVersion(Product product) {
        return jdbc.sql("SELECT coalesce(max(version), 0) + 1 FROM rulesets WHERE product = ?")
            .param(product.name())
            .query(Integer.class)
            .single();
    }

    private static final String SELECT = """
        SELECT id, product, version, status, definition::text AS definition, created_by, created_at, published_by,
               published_at
        FROM rulesets""";

    private StoredRuleset map(ResultSet rs, int row) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new StoredRuleset(rs.getObject("id", UUID.class), Product.valueOf(rs.getString("product")),
            rs.getInt("version"), RulesetStatus.valueOf(rs.getString("status")), json.read(rs.getString("definition")),
            rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(), rs.getString("published_by"),
            publishedAt == null ? null : publishedAt.toInstant());
    }
}
