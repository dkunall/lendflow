package com.lendflow.support;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Request bodies for applications. The PAN's four digits pick the mock bureau's profile (see mock-bureau/), so
 * tests choose a profile by choosing the digits.
 */
public final class Drafts {

    private Drafts() {
    }

    /** A PAN the mock bureau treats as a clean file with a good score. */
    public static String goodPan() {
        return pan(1000 + ThreadLocalRandom.current().nextInt(2000));
    }

    public static String pan(int digits) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            letters.append((char) ('A' + r.nextInt(26)));
        }
        return letters.toString() + String.format("%04d", digits) + (char) ('A' + r.nextInt(26));
    }

    public static String phone() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
    }

    public static Map<String, Object> personal(String name, String pan) {
        return personal(name, pan, "1992-04-17", phone(), name.toLowerCase().replace(' ', '.') + "." + Long.toHexString(System.nanoTime()) + "@example.com");
    }

    public static Map<String, Object> personal(String name, String pan, String dob, String phone, String email) {
        return Map.of("fullName", name, "pan", pan, "dateOfBirth", dob, "phone", phone, "email", email);
    }

    /** Everything a salaried personal-loan application needs except documents. */
    public static Map<String, Object> personalLoan(Map<String, Object> applicant, String amount, int tenure,
                                                   String income, String existingEmi) {
        Map<String, Object> body = new HashMap<>();
        body.put("product", "PERSONAL");
        body.put("amount", amount);
        body.put("tenureMonths", tenure);
        body.put("monthlyIncome", income);
        body.put("existingEmi", existingEmi);
        body.put("employmentType", "SALARIED");
        body.put("monthsInCurrentJob", 30);
        body.put("applicant", applicant);
        body.put("consent", Map.of("bureauPull", true));
        return body;
    }

    public static Map<String, Object> personalLoan(String name) {
        return personalLoan(personal(name, goodPan()), "300000", 36, "90000", "5000");
    }
}
