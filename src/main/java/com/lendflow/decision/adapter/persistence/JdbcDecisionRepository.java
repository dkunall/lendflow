package com.lendflow.decision.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lendflow.decision.application.DecisionRepository;
import com.lendflow.decision.application.DecisionView;
import com.lendflow.decision.application.ShadowReport;
import com.lendflow.decision.application.ShadowScore;
import com.lendflow.decision.domain.Outcome;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.shared.json.Json;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDecisionRepository implements DecisionRepository {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };
    private static final TypeReference<List<Scorecard.Contribution>> CONTRIBUTIONS = new TypeReference<>() { };
    private static final TypeReference<Map<String, List<String>>> FIRED = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final Json json;

    JdbcDecisionRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean insertFirst(DecisionView d) {
        return jdbc.sql("""
                INSERT INTO decisions (application_id, ruleset_id, ruleset_key, scorecard_id, outcome, reasons, score,
                    grade, pd, contributions, fired_rules, annual_rate, fee_percent, max_emi, approved_amount, emi,
                    inputs, decided_at)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(d.applicationId(), d.rulesetId(), d.ruleset(), d.scorecard(), d.outcome().name(),
                json.write(d.reasons()), d.score(), d.grade(), d.probabilityOfDefault(), json.write(d.contributions()),
                json.write(d.firedRules()), d.annualRate(), d.processingFeePercent(), d.maxEmi(), d.approvedAmount(),
                d.emi(), json.write(d.inputs()), Timestamp.from(d.decidedAt()))
            .update() == 1;
    }

    @Override
    public Optional<DecisionView> find(UUID applicationId) {
        return jdbc.sql("""
                SELECT application_id, ruleset_id, ruleset_key, scorecard_id, outcome, reasons::text AS reasons, score,
                       grade, pd, contributions::text AS contributions, fired_rules::text AS fired_rules, annual_rate,
                       fee_percent, max_emi, approved_amount, emi, inputs::text AS inputs, decided_at
                FROM decisions WHERE application_id = ?
                """)
            .param(applicationId)
            .query(this::map)
            .optional();
    }

    @Override
    public void saveShadow(ShadowScore s) {
        jdbc.sql("""
                INSERT INTO shadow_scores (application_id, ruleset_key, champion_id, champion_grade, champion_score,
                    challenger_id, challenger_grade, challenger_score, scored_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (application_id) DO NOTHING
                """)
            .params(s.applicationId(), s.rulesetKey(), s.championId(), s.championGrade(), s.championScore(),
                s.challengerId(), s.challengerGrade(), s.challengerScore(), Timestamp.from(s.scoredAt()))
            .update();
    }

    @Override
    public List<ShadowReport.Cell> shadowCells(String rulesetKey) {
        return jdbc.sql("""
                SELECT champion_id, challenger_id, champion_grade, challenger_grade, count(*) AS n,
                       round(avg(challenger_score - champion_score), 1) AS delta
                FROM shadow_scores WHERE ruleset_key = ?
                GROUP BY champion_id, challenger_id, champion_grade, challenger_grade
                ORDER BY champion_grade, challenger_grade
                """)
            .param(rulesetKey)
            .query((rs, row) -> new ShadowReport.Cell(rs.getString("champion_id"), rs.getString("challenger_id"),
                rs.getString("champion_grade"), rs.getString("challenger_grade"), rs.getLong("n"), rs.getDouble("delta")))
            .list();
    }

    private DecisionView map(ResultSet rs, int row) throws SQLException {
        return new DecisionView(rs.getObject("application_id", UUID.class), rs.getObject("ruleset_id", UUID.class),
            rs.getString("ruleset_key"), rs.getString("scorecard_id"), Outcome.valueOf(rs.getString("outcome")),
            read(rs.getString("reasons"), STRINGS), rs.getInt("score"), rs.getString("grade"), rs.getBigDecimal("pd"),
            read(rs.getString("contributions"), CONTRIBUTIONS), read(rs.getString("fired_rules"), FIRED),
            rs.getBigDecimal("annual_rate"), rs.getBigDecimal("fee_percent"), rs.getBigDecimal("max_emi"),
            rs.getBigDecimal("approved_amount"), rs.getBigDecimal("emi"), json.read(rs.getString("inputs")),
            rs.getTimestamp("decided_at").toInstant());
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.mapper().readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored decision JSON is unreadable", e);
        }
    }
}
