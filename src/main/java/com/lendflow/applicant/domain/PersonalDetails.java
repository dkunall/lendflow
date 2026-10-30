package com.lendflow.applicant.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the applicant tells us about themselves, normalised on the way in so lookups and dedupe compare like
 * with like. Every field is optional while the application is a draft.
 */
public record PersonalDetails(String fullName, String pan, LocalDate dateOfBirth, String phone, String email) {

    private static final Pattern PAN = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]");
    private static final Pattern MOBILE = Pattern.compile("[6-9][0-9]{9}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Set<String> HONORIFICS = Set.of("MR", "MRS", "MS", "MISS", "DR", "SHRI", "SMT");

    public PersonalDetails {
        fullName = fullName == null ? null : fullName.trim().replaceAll("\\s+", " ");
        pan = pan == null ? null : normalisePan(pan);
        phone = phone == null ? null : normalisePhone(phone);
        email = email == null ? null : normaliseEmail(email);
        if (fullName != null && (fullName.length() < 2 || fullName.length() > 120)) {
            throw new IllegalArgumentException("fullName must be 2-120 characters");
        }
    }

    public PersonalDetails merge(PersonalDetails patch) {
        return new PersonalDetails(
            patch.fullName != null ? patch.fullName : fullName,
            patch.pan != null ? patch.pan : pan,
            patch.dateOfBirth != null ? patch.dateOfBirth : dateOfBirth,
            patch.phone != null ? patch.phone : phone,
            patch.email != null ? patch.email : email);
    }

    public boolean complete() {
        return fullName != null && pan != null && dateOfBirth != null && phone != null && email != null;
    }

    /**
     * Upper case letters only, titles dropped, so "Dr. Rahul  Verma" and "RAHUL VERMA" compare equal.
     */
    public static String comparableName(String name) {
        String letters = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z ]", " ").trim();
        StringBuilder out = new StringBuilder();
        for (String part : letters.split("\\s+")) {
            if (part.isEmpty() || HONORIFICS.contains(part)) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part);
        }
        return out.toString();
    }

    /**
     * Gmail ignores dots and anything after '+', so those spellings are the same mailbox for dedupe.
     */
    public static String emailLookupForm(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            int plus = local.indexOf('+');
            local = (plus >= 0 ? local.substring(0, plus) : local).replace(".", "");
            domain = "gmail.com";
        }
        return local + "@" + domain;
    }

    public static String maskPan(String pan) {
        return "XXXXX" + pan.substring(5, 9) + pan.charAt(9);
    }

    public static String maskPhone(String phone) {
        return "XXXXXX" + phone.substring(6);
    }

    private static String normalisePan(String raw) {
        String pan = raw.trim().toUpperCase(Locale.ROOT);
        if (!PAN.matcher(pan).matches()) {
            throw new IllegalArgumentException("pan must look like ABCDE1234F");
        }
        return pan;
    }

    private static String normalisePhone(String raw) {
        String digits = raw.replaceAll("[\\s()-]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!MOBILE.matcher(digits).matches()) {
            throw new IllegalArgumentException("phone must be a 10 digit Indian mobile number");
        }
        return digits;
    }

    private static String normaliseEmail(String raw) {
        String email = raw.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches() || email.length() > 254) {
            throw new IllegalArgumentException("email is not a valid address");
        }
        return email;
    }
}
