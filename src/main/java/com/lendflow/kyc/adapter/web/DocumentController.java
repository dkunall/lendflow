package com.lendflow.kyc.adapter.web;

import com.lendflow.kyc.application.DocumentRecord;
import com.lendflow.kyc.application.DocumentService;
import com.lendflow.kyc.domain.DocumentType;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.error.InvalidRequestException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/applications/{applicationId}/documents")
class DocumentController {

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentRecord> upload(Actor actor, @PathVariable UUID applicationId,
                                          @RequestParam("type") String type,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        DocumentRecord record = documents.upload(actor, applicationId, parse(type), file.getOriginalFilename(),
            file.getBytes());
        return ResponseEntity.status(HttpStatus.CREATED).body(record);
    }

    @GetMapping
    List<DocumentRecord> list(Actor actor, @PathVariable UUID applicationId) {
        return documents.list(actor, applicationId);
    }

    @GetMapping("/{documentId}/content")
    ResponseEntity<byte[]> content(Actor actor, @PathVariable UUID applicationId, @PathVariable UUID documentId) {
        DocumentService.Download download = documents.download(actor, applicationId, documentId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(download.record().mediaType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(download.record().fileName()).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(download.content());
    }

    private static DocumentType parse(String type) {
        try {
            return DocumentType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("document-type", "type must be one of PAN_CARD, INCOME_PROOF, PROPERTY_DOCUMENT");
        }
    }
}
