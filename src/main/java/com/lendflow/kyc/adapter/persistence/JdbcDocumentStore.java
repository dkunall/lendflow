package com.lendflow.kyc.adapter.persistence;

import com.lendflow.kyc.application.DocumentRecord;
import com.lendflow.kyc.application.DocumentStore;
import com.lendflow.kyc.domain.DocumentType;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcDocumentStore implements DocumentStore {

    private final JdbcClient jdbc;

    JdbcDocumentStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replace(DocumentRecord d, byte[] content) {
        // Two uploads of the same type at once would both try to be "current"; the lock makes them take turns.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
            .param(d.applicationId() + ":" + d.type())
            .query((rs, row) -> 1).single();
        jdbc.sql("""
                UPDATE documents SET superseded_at = now()
                 WHERE application_id = ? AND type = ? AND superseded_at IS NULL
                """)
            .params(d.applicationId(), d.type().name())
            .update();
        jdbc.sql("""
                INSERT INTO documents (id, application_id, type, file_name, media_type, size_bytes, sha256, content,
                                       uploaded_by, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(d.id(), d.applicationId(), d.type().name(), d.fileName(), d.mediaType(), d.sizeBytes(), d.sha256(),
                content, d.uploadedBy(), Timestamp.from(d.uploadedAt()))
            .update();
    }

    @Override
    public List<DocumentRecord> current(UUID applicationId) {
        return jdbc.sql("""
                SELECT id, application_id, type, file_name, media_type, size_bytes, sha256, uploaded_by, uploaded_at
                  FROM documents WHERE application_id = ? AND superseded_at IS NULL ORDER BY type
                """)
            .param(applicationId)
            .query((rs, row) -> new DocumentRecord(rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class), DocumentType.valueOf(rs.getString("type")),
                rs.getString("file_name"), rs.getString("media_type"), rs.getLong("size_bytes"), rs.getString("sha256"),
                rs.getString("uploaded_by"), rs.getTimestamp("uploaded_at").toInstant()))
            .list();
    }

    @Override
    public Set<DocumentType> currentTypes(UUID applicationId) {
        Set<DocumentType> types = EnumSet.noneOf(DocumentType.class);
        jdbc.sql("SELECT type FROM documents WHERE application_id = ? AND superseded_at IS NULL")
            .param(applicationId)
            .query(String.class).list()
            .forEach(t -> types.add(DocumentType.valueOf(t)));
        return types;
    }

    @Override
    public Optional<byte[]> content(UUID applicationId, UUID documentId) {
        return jdbc.sql("SELECT content FROM documents WHERE application_id = ? AND id = ?")
            .params(applicationId, documentId)
            .query((rs, row) -> rs.getBytes("content"))
            .optional();
    }
}
