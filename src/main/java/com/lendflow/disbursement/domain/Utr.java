package com.lendflow.disbursement.domain;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Bank transfer reference. IMPS references are 12 digits, NEFT 16 and RTGS 22 characters; this checks only the
 * shape, since the bank is the authority on whether one is real.
 */
public record Utr(String value) {

    private static final Pattern SHAPE = Pattern.compile("[A-Z0-9]{12,22}");

    public static Optional<Utr> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalised = raw.strip().toUpperCase(Locale.ROOT);
        return SHAPE.matcher(normalised).matches() ? Optional.of(new Utr(normalised)) : Optional.empty();
    }
}
