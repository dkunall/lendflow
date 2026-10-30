package com.lendflow.decision.application;

import com.lendflow.decision.domain.Ruleset;
import com.lendflow.decision.domain.Scorecard;
import com.lendflow.shared.domain.Product;
import com.lendflow.shared.error.NotFoundException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Looks up the published ruleset for a product on every call, so a publish takes effect for the next
 * decision. Parsed policies are cached by row id, which is safe because non-draft rows cannot change.
 */
@Service
public class PolicyCatalog {

    private final PolicyRepository repository;
    private final Map<UUID, ActivePolicy> parsed = new ConcurrentHashMap<>();

    public PolicyCatalog(PolicyRepository repository) {
        this.repository = repository;
    }

    public ActivePolicy published(Product product) {
        StoredRuleset stored = repository.published(product)
            .orElseThrow(() -> new IllegalStateException("No published ruleset for " + product));
        return parsed.computeIfAbsent(stored.id(), id -> parse(stored));
    }

    /** Parses any version, drafts included, without caching them since drafts can still be edited. */
    public ActivePolicy load(UUID rulesetId) {
        StoredRuleset stored = repository.find(rulesetId)
            .orElseThrow(() -> new NotFoundException("ruleset-not-found", "No ruleset " + rulesetId));
        return stored.status() == RulesetStatus.DRAFT ? parse(stored) : parsed.computeIfAbsent(rulesetId, id -> parse(stored));
    }

    ActivePolicy parse(StoredRuleset stored) {
        Ruleset ruleset = PolicyFormat.ruleset(stored.definition());
        if (ruleset.product() != stored.product() || ruleset.version() != stored.version()) {
            throw new IllegalArgumentException("Definition says " + ruleset.key() + " but the row is " + stored.key());
        }
        Scorecard champion = scorecard(ruleset.championScorecard());
        Scorecard challenger = ruleset.challengerScorecard() == null ? null : scorecard(ruleset.challengerScorecard());
        return new ActivePolicy(stored, ruleset, champion, challenger);
    }

    private Scorecard scorecard(String id) {
        return PolicyFormat.scorecard(repository.scorecard(id)
            .orElseThrow(() -> new IllegalArgumentException("Unknown scorecard " + id)));
    }
}
