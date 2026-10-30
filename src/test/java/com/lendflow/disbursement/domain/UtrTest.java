package com.lendflow.disbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class UtrTest {

    @ParameterizedTest
    @ValueSource(strings = {"412345678901", "SBIN226012345678", "HDFCR52026103012345678", " sbin226012345678 "})
    void acceptsImpsNeftAndRtgsShapes(String raw) {
        assertThat(Utr.parse(raw)).isPresent();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "12345", "SBIN-2260-1234-5678", "HDFCR52026103012345678X", "SBIN22601234567 8"})
    void rejectsAnythingElse(String raw) {
        assertThat(Utr.parse(raw)).isEmpty();
    }

    @Test
    void normalisesCaseSoTheSameReferenceCompareEqual() {
        assertThat(Utr.parse("sbin226012345678")).isEqualTo(Utr.parse("SBIN226012345678"));
    }

    @Test
    void aDisbursementIsSentOnlyOnce() {
        Instant now = Instant.parse("2026-10-30T06:00:00Z");
        Disbursement sent = Disbursement.requested(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PERSONAL",
                new BigDecimal("296460.00"), now)
            .sent(Utr.parse("412345678901").orElseThrow(), "ops-1", now);
        assertThat(sent.status()).isEqualTo(DisbursementStatus.SENT);
        assertThatThrownBy(() -> sent.sent(Utr.parse("412345678902").orElseThrow(), "ops-2", now))
            .isInstanceOf(IllegalStateException.class);
    }
}
