package com.lendflow.bureau.application;

import com.lendflow.bureau.domain.CreditReport;
import java.util.Optional;
import java.util.UUID;

public interface CreditReportRepository {

    CreditReport saveFirst(CreditReport report);

    Optional<CreditReport> find(UUID applicationId);
}
