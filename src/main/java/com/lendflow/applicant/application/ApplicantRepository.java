package com.lendflow.applicant.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicantRepository {

    void upsert(ApplicantRecord applicant);

    Optional<ApplicantRecord> find(UUID applicationId);

    void markSubmitted(UUID applicationId, Instant at);

    /**
     * Other submitted applicants since {@code since} that share a PAN, phone or email hash, or the date of
     * birth (the block the fuzzy name check runs within).
     */
    List<ApplicantRecord> candidates(UUID excludeApplicationId, String panHmac, String phoneHmac, String emailHmac,
                                     LocalDate dateOfBirth, Instant since);
}
