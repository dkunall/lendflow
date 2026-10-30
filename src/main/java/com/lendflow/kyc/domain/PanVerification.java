package com.lendflow.kyc.domain;

/**
 * What the PAN registry said. {@code nameOnRecord} is null when the PAN does not exist.
 */
public record PanVerification(boolean valid, String nameOnRecord) {
}
