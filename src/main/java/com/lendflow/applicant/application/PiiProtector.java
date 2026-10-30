package com.lendflow.applicant.application;

/**
 * Keeps PAN, phone and email out of the database in the clear: a keyed hash for equality lookups and an
 * authenticated ciphertext for the few places that need the value back (the bureau pull).
 */
public interface PiiProtector {

    /** Keyed, so a leaked table cannot be reversed by hashing every possible PAN or phone number. */
    String lookupHash(String normalisedValue);

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
