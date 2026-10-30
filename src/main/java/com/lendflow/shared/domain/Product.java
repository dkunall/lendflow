package com.lendflow.shared.domain;

/**
 * Loan products. Each has its own ruleset, required documents and offer pricing steps.
 */
public enum Product {
    PERSONAL, HOME;

    public boolean isSecured() {
        return this == HOME;
    }
}
