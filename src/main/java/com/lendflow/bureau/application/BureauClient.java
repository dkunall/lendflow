package com.lendflow.bureau.application;

public interface BureauClient {

    /**
     * @throws com.lendflow.shared.error.UpstreamUnavailableException when the bureau cannot be reached after
     *     retries, the circuit is open, or too many pulls are already in flight
     */
    BureauResponse fetch(BureauRequest request);
}
