package com.lendflow.underwriting.domain;

import com.lendflow.shared.domain.Actor.Role;
import java.util.List;
import java.util.Set;

/** Credit roles from least to most authority. Someone higher up can pick up work meant for a lower role. */
public final class Authority {

    private static final List<Role> LADDER = List.of(Role.UNDERWRITER, Role.CREDIT_MANAGER, Role.CREDIT_HEAD);

    private Authority() {
    }

    public static boolean covers(Set<Role> roles, Role required) {
        return roles.stream().filter(LADDER::contains).anyMatch(r -> LADDER.indexOf(r) >= LADDER.indexOf(required));
    }

    /** Roles whose queues these roles may work. */
    public static List<Role> claimable(Set<Role> roles) {
        return LADDER.stream().filter(r -> covers(roles, r)).toList();
    }

    public static Role above(Role role) {
        int next = Math.min(LADDER.indexOf(role) + 1, LADDER.size() - 1);
        return LADDER.get(next);
    }
}
