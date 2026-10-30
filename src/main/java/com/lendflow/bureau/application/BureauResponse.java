package com.lendflow.bureau.application;

import java.math.BigDecimal;

public record BureauResponse(String bureauRef, boolean hit, Integer score, Integer activeAccounts,
                             BigDecimal totalOutstanding, Integer maxDpdLast12m, Integer enquiriesLast6m,
                             Integer monthsOnBureau, Boolean writtenOff, BigDecimal reportedEmi) {
}
