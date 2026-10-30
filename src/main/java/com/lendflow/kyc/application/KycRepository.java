package com.lendflow.kyc.application;

import java.util.Optional;
import java.util.UUID;

public interface KycRepository {

    /** Keeps the first result if two runs race; returns whichever is stored. */
    KycResult saveFirst(KycResult result);

    Optional<KycResult> find(UUID applicationId);
}
