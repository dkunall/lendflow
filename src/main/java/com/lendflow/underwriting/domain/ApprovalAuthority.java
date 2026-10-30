package com.lendflow.underwriting.domain;

import com.lendflow.shared.domain.Actor.Role;
import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Who may sign off a referred loan, as a chain: each link takes amounts within its limit and hands the rest
 * up. The last link has no limit, so every amount resolves to someone.
 */
public abstract sealed class ApprovalAuthority {

    public abstract Role checkerFor(Product product, BigDecimal amount);

    public static ApprovalAuthority chain(Map<Product, BigDecimal> managerLimits) {
        return new Limited(Role.CREDIT_MANAGER, managerLimits, new Unlimited(Role.CREDIT_HEAD));
    }

    static final class Limited extends ApprovalAuthority {

        private final Role role;
        private final Map<Product, BigDecimal> limits;
        private final ApprovalAuthority next;

        Limited(Role role, Map<Product, BigDecimal> limits, ApprovalAuthority next) {
            this.role = role;
            this.limits = Map.copyOf(limits);
            this.next = next;
        }

        @Override
        public Role checkerFor(Product product, BigDecimal amount) {
            BigDecimal limit = limits.get(product);
            if (amount == null || (limit != null && amount.compareTo(limit) <= 0)) {
                return role;
            }
            return next.checkerFor(product, amount);
        }
    }

    static final class Unlimited extends ApprovalAuthority {

        private final Role role;

        Unlimited(Role role) {
            this.role = role;
        }

        @Override
        public Role checkerFor(Product product, BigDecimal amount) {
            return role;
        }
    }
}
