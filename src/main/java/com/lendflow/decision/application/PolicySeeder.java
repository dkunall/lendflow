package com.lendflow.decision.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.lendflow.decision.domain.Ruleset;
import com.lendflow.shared.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the policies shipped in {@code classpath:policies} on startup. They live as JSON rather than in a
 * migration so the golden tests read exactly the same files. Existing rows are left alone, which makes
 * restarts and several instances starting together harmless.
 */
@Component
class PolicySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicySeeder.class);

    private final PolicyRepository repository;
    private final Json json;

    PolicySeeder(PolicyRepository repository, Json json) {
        this.repository = repository;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        List<JsonNode> rulesets = new ArrayList<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath:policies/*.json")) {
            JsonNode node = read(resource);
            if (node.has("characteristics")) {
                String id = PolicyFormat.scorecard(node).id();
                if (repository.addScorecard(id, node)) {
                    log.info("Seeded scorecard {}", id);
                }
            } else {
                rulesets.add(node);
            }
        }
        for (JsonNode node : rulesets) {
            Ruleset ruleset = PolicyFormat.ruleset(node);
            UUID id = UUID.nameUUIDFromBytes(("seed:" + ruleset.key()).getBytes(StandardCharsets.UTF_8));
            if (repository.seedPublished(id, ruleset.product(), ruleset.version(), node)) {
                log.info("Seeded and published ruleset {}", ruleset.key());
            }
        }
    }

    private JsonNode read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return json.mapper().readTree(in);
        }
    }
}
