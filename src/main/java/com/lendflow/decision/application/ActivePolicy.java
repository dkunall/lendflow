package com.lendflow.decision.application;

import com.lendflow.decision.domain.Ruleset;
import com.lendflow.decision.domain.Scorecard;
import java.util.Optional;

/** A ruleset row parsed together with the scorecards it names. */
public record ActivePolicy(StoredRuleset stored, Ruleset ruleset, Scorecard champion, Scorecard challenger) {

    public Optional<Scorecard> challengerScorecard() {
        return Optional.ofNullable(challenger);
    }
}
