package com.lendflow.shared.messaging;

/**
 * Event contracts between modules. Consumers depend on these payloads, never on another module's tables.
 */
public final class Topics {

    /** Application lifecycle events, keyed by application id so one application's events stay in order. */
    public static final String APPLICATIONS = "lendflow.applications";

    /** Accepted offers, the handoff to disbursement. Keyed by application id. */
    public static final String SANCTIONS = "lendflow.sanctions";

    private Topics() {
    }
}
