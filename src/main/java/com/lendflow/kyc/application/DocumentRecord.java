package com.lendflow.kyc.application;

import com.lendflow.kyc.domain.DocumentType;
import java.time.Instant;
import java.util.UUID;

/** Metadata only; content is loaded separately so listing documents never pulls megabytes. */
public record DocumentRecord(UUID id, UUID applicationId, DocumentType type, String fileName, String mediaType,
                             long sizeBytes, String sha256, String uploadedBy, Instant uploadedAt) {
}
