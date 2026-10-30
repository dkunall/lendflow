package com.lendflow.kyc.application;

import com.lendflow.kyc.domain.DocumentType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface DocumentStore {

    /** Replaces the current document of this type, keeping the old one as superseded. */
    void replace(DocumentRecord document, byte[] content);

    List<DocumentRecord> current(UUID applicationId);

    Set<DocumentType> currentTypes(UUID applicationId);

    Optional<byte[]> content(UUID applicationId, UUID documentId);
}
