package com.lendflow.kyc.application;

import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.kyc.domain.DocumentType;
import com.lendflow.kyc.domain.FileKind;
import com.lendflow.shared.crypto.Sha256;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.InvalidRequestException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;

@Service
public class DocumentService {

    private final ApplicationService applications;
    private final DocumentStore store;
    private final Clock clock;
    private final long maxBytes;

    DocumentService(ApplicationService applications, DocumentStore store, Clock clock,
                    @Value("${lendflow.documents.max-size:2MB}") DataSize maxSize) {
        this.applications = applications;
        this.store = store;
        this.clock = clock;
        this.maxBytes = maxSize.toBytes();
    }

    @Transactional
    public DocumentRecord upload(Actor actor, UUID applicationId, DocumentType type, String fileName, byte[] content) {
        LoanApplication application = applications.owned(actor, applicationId);
        if (application.status() != ApplicationStatus.DRAFT) {
            throw new StateConflictException("not-a-draft", "Documents can only be added to a draft");
        }
        if (!DocumentType.requiredFor(application.product()).contains(type)) {
            throw new InvalidRequestException("document-not-applicable", type + " is not used for " + application.product());
        }
        if (content.length == 0 || content.length > maxBytes) {
            throw new InvalidRequestException("document-size", "Documents must be between 1 byte and " + maxBytes + " bytes");
        }
        FileKind kind = FileKind.detect(content).orElseThrow(() ->
            new InvalidRequestException("document-format", "Only PDF, JPEG and PNG files are accepted"));
        DocumentRecord record = new DocumentRecord(UUID.randomUUID(), applicationId, type, safeName(fileName),
            kind.mediaType(), content.length, Sha256.hex(content), actor.subject(), clock.instant());
        store.replace(record, content);
        return record;
    }

    @Transactional(readOnly = true)
    public List<DocumentRecord> list(Actor actor, UUID applicationId) {
        applications.readable(actor, applicationId);
        return store.current(applicationId);
    }

    @Transactional(readOnly = true)
    public Download download(Actor actor, UUID applicationId, UUID documentId) {
        applications.readable(actor, applicationId);
        DocumentRecord record = store.current(applicationId).stream().filter(d -> d.id().equals(documentId))
            .findFirst().orElseThrow(() -> new NotFoundException("document-not-found", "No document " + documentId));
        byte[] content = store.content(applicationId, documentId).orElseThrow();
        return new Download(record, content);
    }

    public record Download(DocumentRecord record, byte[] content) {
    }

    // Keeps the name readable in an underwriter's download but strips anything a path or header could misuse.
    private static String safeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "document";
        }
        String base = fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 100 ? base.substring(base.length() - 100) : base;
    }
}
