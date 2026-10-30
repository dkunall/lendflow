package com.lendflow.applicant.domain;

/**
 * Jaro-Winkler similarity in [0, 1]. Suits short strings like names, where a shared prefix is strong evidence
 * and transposed letters ("Rhaul" for "Rahul") are a common typo.
 */
public final class JaroWinkler {

    private static final double PREFIX_SCALE = 0.1;
    private static final int MAX_PREFIX = 4;

    private JaroWinkler() {
    }

    public static double similarity(String a, String b) {
        double jaro = jaro(a, b);
        int prefix = 0;
        int limit = Math.min(MAX_PREFIX, Math.min(a.length(), b.length()));
        while (prefix < limit && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * PREFIX_SCALE * (1 - jaro);
    }

    static double jaro(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int window = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1);
        boolean[] matchedA = new boolean[a.length()];
        boolean[] matchedB = new boolean[b.length()];
        int matches = 0;
        for (int i = 0; i < a.length(); i++) {
            int from = Math.max(0, i - window);
            int to = Math.min(b.length() - 1, i + window);
            for (int j = from; j <= to; j++) {
                if (!matchedB[j] && a.charAt(i) == b.charAt(j)) {
                    matchedA[i] = true;
                    matchedB[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < a.length(); i++) {
            if (!matchedA[i]) {
                continue;
            }
            while (!matchedB[k]) {
                k++;
            }
            if (a.charAt(i) != b.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        double m = matches;
        return (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
    }
}
