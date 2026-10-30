package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lendflow.decision.application.DecisionRepository.RecordedInputs;
import com.lendflow.decision.domain.CreditFacts;
import com.lendflow.decision.domain.Simulation;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.domain.Product;
import com.lendflow.shared.error.ForbiddenException;
import com.lendflow.shared.error.InvalidRequestException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import com.lendflow.shared.json.Json;
import com.lendflow.shared.messaging.Topics;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rulesets are data: drafted, replayed against past decisions, then published by a second person. Once
 * published a version never changes; the next change is a new version.
 */
@Service
public class RulesetService {

    private static final int MAX_SIMULATION = 5000;
    private static final int SAMPLE = 20;

    public record Draft(Product product, JsonNode definition) {
    }

    public record SimulationResult(String ruleset, String comparedWith, Simulation.Report report) {
    }

    private final PolicyRepository repository;
    private final PolicyCatalog catalog;
    private final DecisionRepository decisions;
    private final OutboxWriter outbox;
    private final Json json;
    private final Clock clock;

    RulesetService(PolicyRepository repository, PolicyCatalog catalog, DecisionRepository decisions,
                   OutboxWriter outbox, Json json, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.decisions = decisions;
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public StoredRuleset draft(Actor actor, Draft draft) {
        if (draft == null || draft.product() == null || draft.definition() == null || !draft.definition().isObject()) {
            throw new InvalidRequestException("ruleset-invalid", "A product and a definition object are required");
        }
        repository.lockProduct(draft.product());
        int version = repository.nextVersion(draft.product());
        ObjectNode definition = ((ObjectNode) draft.definition().deepCopy())
            .put("product", draft.product().name())
            .put("version", version);
        StoredRuleset stored = new StoredRuleset(UUID.randomUUID(), draft.product(), version, RulesetStatus.DRAFT,
            definition, actor.subject(), clock.instant(), null, null);
        try {
            catalog.parse(stored);
        } catch (RuntimeException e) {
            throw new InvalidRequestException("ruleset-invalid", e.getMessage());
        }
        repository.insertDraft(stored);
        return stored;
    }

    @Transactional(readOnly = true)
    public List<StoredRuleset> list(Product product) {
        return repository.all(product);
    }

    @Transactional(readOnly = true)
    public StoredRuleset get(UUID id) {
        return repository.find(id).orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public SimulationResult simulate(UUID id, int limit) {
        ActivePolicy candidate = catalog.load(id);
        Product product = candidate.stored().product();
        List<Simulation.Past> history = decisions.recentInputs(product, Math.clamp(limit, 1, MAX_SIMULATION)).stream()
            .map(this::past)
            .toList();
        String current = repository.published(product).map(StoredRuleset::key).orElse(null);
        return new SimulationResult(candidate.stored().key(), current,
            Simulation.run(candidate.ruleset(), candidate.champion(), history, SAMPLE));
    }

    @Transactional
    public StoredRuleset publish(Actor actor, UUID id) {
        StoredRuleset draft = get(id);
        repository.lockProduct(draft.product());
        draft = get(id);
        if (draft.status() != RulesetStatus.DRAFT) {
            throw new StateConflictException("ruleset-not-draft", draft.key() + " is " + draft.status());
        }
        if (draft.createdBy().equals(actor.subject())) {
            throw new ForbiddenException("four-eyes", "A ruleset must be published by someone other than its author");
        }
        String previous = repository.published(draft.product()).map(StoredRuleset::key).orElse(null);
        Instant now = clock.instant();
        repository.publish(draft, actor.subject(), now);
        outbox.append(Topics.APPLICATIONS, draft.product().name(), "RulesetPublished", Map.of(
            "ruleset", draft.key(), "rulesetId", draft.id(), "replaces", previous == null ? "" : previous,
            "author", draft.createdBy(), "publishedBy", actor.subject(), "at", now.toString()));
        return get(id);
    }

    private Simulation.Past past(RecordedInputs recorded) {
        return new Simulation.Past(recorded.applicationId(), recorded.outcome(),
            json.convert(recorded.inputs(), CreditFacts.class));
    }

    private static NotFoundException notFound(UUID id) {
        return new NotFoundException("ruleset-not-found", "No ruleset " + id);
    }
}
