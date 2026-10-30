package com.lendflow.decision.application;

import java.util.List;

/**
 * Champion grade against challenger grade for every application scored under one ruleset. The challenger
 * never affects a decision; this is how a policy team judges whether to promote it.
 */
public record ShadowReport(String ruleset, String champion, String challenger, long applications, long sameGrade,
                           List<Cell> cells) {

    public record Cell(String championId, String challengerId, String championGrade, String challengerGrade,
                       long count, double averageScoreDelta) {
    }
}
