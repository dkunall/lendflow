package com.lendflow.application.domain;

public enum ApplicationStatus {
    DRAFT, SUBMITTED, UNDERWRITING, APPROVED, REFER, REJECTED, SANCTIONED, DISBURSED, WITHDRAWN;

    public boolean isTerminal() {
        return this == REJECTED || this == DISBURSED || this == WITHDRAWN;
    }
}
