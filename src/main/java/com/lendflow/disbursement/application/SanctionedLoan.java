package com.lendflow.disbursement.application;

import java.math.BigDecimal;
import java.util.UUID;

/** The part of the LoanSanctioned payload disbursement needs. */
public record SanctionedLoan(UUID applicationId, UUID offerId, String product, BigDecimal netDisbursal) {
}
