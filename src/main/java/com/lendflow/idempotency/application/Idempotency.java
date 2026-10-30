package com.lendflow.idempotency.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendflow.shared.crypto.Sha256;
import com.lendflow.shared.error.BusinessRuleException;
import com.lendflow.shared.error.InvalidRequestException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs an effect at most once per key. The key row is written in the same transaction as the effect, so a
 * rolled-back attempt leaves nothing behind and a retry runs it again; a committed one is replayed.
 */
@Component
public class Idempotency {

    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.:-]{8,100}");

    private final IdempotencyStore store;
    private final ObjectMapper json;

    Idempotency(IdempotencyStore store, ObjectMapper json) {
        this.store = store;
        this.json = json;
    }

    public static String requireKey(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new InvalidRequestException("idempotency-key-invalid",
                "Idempotency-Key header is required: 8-100 characters of letters, digits, '_', '.', ':' or '-'");
        }
        return key;
    }

    /**
     * @param scope who and what the key belongs to, e.g. "accept:{subject}", so two users cannot collide
     * @param request what the client sent; reusing a key with a different request is rejected
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T once(String scope, String key, Object request, Class<T> type, Supplier<T> effect) {
        String hash = Sha256.hex(write(request));
        Optional<IdempotencyStore.Stored> existing = store.claim(scope, requireKey(key), hash);
        if (existing.isPresent()) {
            IdempotencyStore.Stored stored = existing.get();
            if (!stored.requestHash().equals(hash)) {
                throw new BusinessRuleException("idempotency-key-reused",
                    "This Idempotency-Key was already used for a different request");
            }
            return read(stored.responseJson(), type);
        }
        T result = effect.get();
        store.complete(scope, key, write(result));
        return result;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Not serialisable", e);
        }
    }

    private <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored idempotent response no longer matches " + type.getSimpleName(), e);
        }
    }
}
