package com.lendflow.kyc.application;

import com.lendflow.kyc.domain.PanVerification;

public interface PanRegistry {

    /**
     * @throws com.lendflow.shared.error.UpstreamUnavailableException when the registry cannot answer; the
     *     caller retries later rather than failing the applicant
     */
    PanVerification verify(String pan, String declaredName);
}
