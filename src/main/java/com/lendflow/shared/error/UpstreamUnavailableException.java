package com.lendflow.shared.error;

/**
 * A dependency outside our control (the credit bureau) failed. Reported as 502 so clients can tell it apart
 * from our own errors.
 */
public class UpstreamUnavailableException extends DomainException {

    public UpstreamUnavailableException(String code, String message) {
        super(code, message);
    }
}
