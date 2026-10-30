package com.lendflow.application.application;

import com.lendflow.application.domain.LoanApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationRepository {

    void insert(LoanApplication application);

    Optional<LoanApplication> find(UUID id);

    /**
     * Writes the new state only if nobody changed the row since it was read at {@code expectedVersion}.
     * Returns false on a lost race; the stored version becomes {@code expectedVersion + 1} on success.
     */
    boolean update(LoanApplication application, int expectedVersion);

    List<LoanApplication> listByOwner(String ownerSubject, int limit);

    void appendHistory(HistoryEntry entry);

    List<HistoryEntry> history(UUID applicationId);
}
