package com.lendflow.bureau.application;

import java.time.Instant;
import java.time.LocalDate;

/** Consent travels with every pull; a bureau is entitled to ask for proof of it. */
public record BureauRequest(String pan, String fullName, LocalDate dateOfBirth, String consentVersion,
                            Instant consentAt) {
}
