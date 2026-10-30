package com.lendflow.applicant.adapter.crypto;

import com.lendflow.applicant.application.PiiProtector;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 for lookups and AES-256-GCM for storage, with separate keys so one leaking does not expose the
 * other. Ciphertexts carry a version prefix so a key rotation can tell old rows from new.
 */
@Component
class KeyedPiiProtector implements PiiProtector {

    private static final String VERSION = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec dataKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom random = new SecureRandom();

    KeyedPiiProtector(@Value("${lendflow.crypto.data-key}") String dataKey,
                      @Value("${lendflow.crypto.lookup-key}") String lookupKey) {
        this.dataKey = new SecretKeySpec(decodeKey(dataKey, "data-key"), "AES");
        this.lookupKey = new SecretKeySpec(decodeKey(lookupKey, "lookup-key"), "HmacSHA256");
    }

    @Override
    public String lookupHash(String normalisedValue) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookupKey);
            return HexFormat.of().formatHex(mac.doFinal(normalisedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length)
                .put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!ciphertext.startsWith(VERSION)) {
            throw new IllegalStateException("Unknown ciphertext version");
        }
        byte[] raw = Base64.getDecoder().decode(ciphertext.substring(VERSION.length()));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Ciphertext failed authentication", e);
        }
    }

    private static byte[] decodeKey(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("lendflow.crypto." + name + " is not set");
        }
        byte[] key = Base64.getDecoder().decode(base64.trim());
        if (key.length != 32) {
            throw new IllegalStateException("lendflow.crypto." + name + " must be 32 bytes, base64 encoded");
        }
        return key;
    }
}
