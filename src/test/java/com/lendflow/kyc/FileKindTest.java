package com.lendflow.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendflow.kyc.domain.FileKind;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FileKindTest {

    @Test
    void recognisesTheThreeAcceptedFormats() {
        assertThat(FileKind.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(FileKind.PDF);
        assertThat(FileKind.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains(FileKind.JPEG);
        assertThat(FileKind.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0}))
            .contains(FileKind.PNG);
    }

    @Test
    void rejectsAnythingElseWhateverItIsCalled() {
        assertThat(FileKind.detect("<html><script>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileKind.detect(new byte[] {'P', 'K', 3, 4})).isEmpty();
        assertThat(FileKind.detect(new byte[] {'%', 'P'})).isEmpty();
    }
}
