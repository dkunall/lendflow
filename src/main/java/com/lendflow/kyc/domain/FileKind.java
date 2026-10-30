package com.lendflow.kyc.domain;

import java.util.Optional;

/**
 * Detects the file type from its first bytes. The client's Content-Type is ignored, because it is whatever the
 * browser guessed from the file name.
 */
public enum FileKind {
    PDF("application/pdf", new byte[] {'%', 'P', 'D', 'F', '-'}),
    JPEG("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
    PNG("image/png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

    private final String mediaType;
    private final byte[] magic;

    FileKind(String mediaType, byte[] magic) {
        this.mediaType = mediaType;
        this.magic = magic;
    }

    public String mediaType() {
        return mediaType;
    }

    public static Optional<FileKind> detect(byte[] content) {
        for (FileKind kind : values()) {
            if (startsWith(content, kind.magic)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (content[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
