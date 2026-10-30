package com.lendflow.idempotency.application;

import java.util.Optional;

public interface IdempotencyStore {

    /**
     * Inserts the key, or returns the row already holding it. Called inside the caller's transaction; a
     * concurrent holder of the same key makes this wait until that transaction ends.
     */
    Optional<Stored> claim(String scope, String key, String requestHash);

    void complete(String scope, String key, String responseJson);

    record Stored(String requestHash, String responseJson) {
    }
}
