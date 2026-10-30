package com.lendflow.disbursement.application;

import com.lendflow.disbursement.domain.Disbursement;
import com.lendflow.disbursement.domain.DisbursementStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository {

    /** False when the application already has one; a re-sent sanction never creates a second payout. */
    boolean insertFirst(Disbursement disbursement);

    Optional<Disbursement> find(UUID id);

    Optional<Disbursement> lock(UUID id);

    Optional<Disbursement> forApplication(UUID applicationId);

    boolean utrTaken(String utr);

    void markSent(Disbursement sent);

    List<Disbursement> withStatus(DisbursementStatus status, int limit);
}
