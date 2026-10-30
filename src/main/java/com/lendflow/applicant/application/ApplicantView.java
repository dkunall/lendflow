package com.lendflow.applicant.application;

import java.time.LocalDate;

/**
 * What APIs show: enough to recognise the person, never the full PAN or phone number.
 */
public record ApplicantView(String fullName, String panMasked, LocalDate dateOfBirth, String phoneMasked,
                            boolean emailProvided) {
}
