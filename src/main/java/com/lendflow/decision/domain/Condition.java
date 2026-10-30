package com.lendflow.decision.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * One cell of a decision table, in a small subset of DMN's unary tests:
 * {@code -} (anything), {@code <650}, {@code >=0.5}, {@code [21..60]}, {@code [0.2..0.4)}, {@code 5},
 * {@code true}, {@code null}, and {@code A,B} for a set of names. Parsed once when the ruleset is loaded, so a
 * typo fails the load rather than a live decision.
 */
public record Condition(String text, Predicate<Object> test) {

    private static final Pattern COMPARISON = Pattern.compile("(<=|>=|<|>)\\s*(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern RANGE = Pattern.compile("([\\[(])\\s*(-?[0-9.]+)\\s*\\.\\.\\s*(-?[0-9.]+)\\s*([])])");
    private static final Pattern NUMBER = Pattern.compile("-?[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern NAMES = Pattern.compile("[A-Z_][A-Z0-9_]*(\\s*,\\s*[A-Z_][A-Z0-9_]*)*");

    public static final Condition ANY = new Condition("-", v -> true);

    public boolean matches(Object value) {
        return test.test(value);
    }

    public static Condition parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.equals("-")) {
            return ANY;
        }
        if (text.equals("null")) {
            return new Condition(text, v -> v == null);
        }
        if (text.equals("true") || text.equals("false")) {
            Boolean expected = Boolean.valueOf(text);
            return new Condition(text, expected::equals);
        }
        Matcher m = COMPARISON.matcher(text);
        if (m.matches()) {
            BigDecimal bound = new BigDecimal(m.group(2));
            String op = m.group(1);
            return numeric(text, n -> switch (op) {
                case "<" -> n.compareTo(bound) < 0;
                case "<=" -> n.compareTo(bound) <= 0;
                case ">" -> n.compareTo(bound) > 0;
                default -> n.compareTo(bound) >= 0;
            });
        }
        m = RANGE.matcher(text);
        if (m.matches()) {
            BigDecimal low = new BigDecimal(m.group(2));
            BigDecimal high = new BigDecimal(m.group(3));
            boolean lowIn = m.group(1).equals("[");
            boolean highIn = m.group(4).equals("]");
            if (low.compareTo(high) > 0) {
                throw new IllegalArgumentException("Empty range: " + text);
            }
            return numeric(text, n -> {
                int lo = n.compareTo(low);
                int hi = n.compareTo(high);
                return (lowIn ? lo >= 0 : lo > 0) && (highIn ? hi <= 0 : hi < 0);
            });
        }
        if (NUMBER.matcher(text).matches()) {
            BigDecimal expected = new BigDecimal(text);
            return numeric(text, n -> n.compareTo(expected) == 0);
        }
        if (NAMES.matcher(text).matches()) {
            Set<String> names = Arrays.stream(text.split(",")).map(String::trim).collect(Collectors.toUnmodifiableSet());
            return new Condition(text, v -> v != null && names.contains(v.toString()));
        }
        throw new IllegalArgumentException("Cannot read condition '" + raw + "'");
    }

    private static Condition numeric(String text, Predicate<BigDecimal> test) {
        return new Condition(text, v -> v instanceof Number n && test.test(new BigDecimal(n.toString())));
    }

    @Override
    public String toString() {
        return text;
    }
}
