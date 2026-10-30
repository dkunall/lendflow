package com.lendflow.applicant.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class KeyedPiiProtectorTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeyedPiiProtector protector = new KeyedPiiProtector(key(), key());

    @Test
    void roundTripsAndUsesAFreshNonceEachTime() {
        String first = protector.encrypt("ABCDE1234F");
        String second = protector.encrypt("ABCDE1234F");

        assertThat(first).isNotEqualTo(second).doesNotContain("ABCDE1234F").startsWith("v1:");
        assertThat(protector.decrypt(first)).isEqualTo("ABCDE1234F");
        assertThat(protector.decrypt(second)).isEqualTo("ABCDE1234F");
    }

    @Test
    void lookupHashIsStableButDependsOnTheKey() {
        KeyedPiiProtector other = new KeyedPiiProtector(key(), key());

        assertThat(protector.lookupHash("9876543210")).isEqualTo(protector.lookupHash("9876543210"));
        assertThat(protector.lookupHash("9876543210")).isNotEqualTo(other.lookupHash("9876543210"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String sealed = protector.encrypt("9876543210");
        byte[] raw = Base64.getDecoder().decode(sealed.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);

        assertThatThrownBy(() -> protector.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithAShortOrMissingKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new KeyedPiiProtector(shortKey, key())).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new KeyedPiiProtector("", key())).hasMessageContaining("not set");
    }

    private static String key() {
        byte[] key = new byte[32];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
