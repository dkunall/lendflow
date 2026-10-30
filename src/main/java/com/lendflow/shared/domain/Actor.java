package com.lendflow.shared.domain;

import java.util.Objects;
import java.util.Set;

/**
 * The caller as the token describes them. Applicants own the applications they created, found by
 * {@code subject}; staff roles map one to one to approval queues.
 */
public record Actor(String subject, Set<Role> roles) {

    public enum Role {
        APPLICANT, UNDERWRITER, CREDIT_MANAGER, CREDIT_HEAD, POLICY_ADMIN, OPS;

        public boolean isStaff() {
            return this != APPLICANT;
        }
    }

    public Actor {
        Objects.requireNonNull(subject, "subject");
        roles = Set.copyOf(roles);
    }

    public static Actor system() {
        return new Actor("system", Set.of());
    }

    public boolean is(Role role) {
        return roles.contains(role);
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(Role::isStaff);
    }
}
