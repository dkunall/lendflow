package com.lendflow.applicant.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicantRecord(UUID applicationId, String ownerSubject, String fullName, String comparableName,
                              LocalDate dateOfBirth, String panHmac, String panEnc, String phoneHmac,
                              String phoneEnc, String emailHmac, String emailEnc, Instant submittedAt) {
}
