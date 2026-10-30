package com.lendflow.shared.error;

import java.util.Map;

/**
 * Base for failures the API reports to clients. {@code code} becomes the problem type, so keep it stable.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    protected DomainException(String code, String message) {
        this(code, message, Map.of());
    }

    protected DomainException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public String code() {
        return code;
    }

    /**
     * Extra members for the problem body, such as the fields still missing from a draft.
     */
    public Map<String, Object> details() {
        return details;
    }
}
