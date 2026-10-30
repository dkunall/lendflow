package com.lendflow.offer.application;

import com.lendflow.offer.domain.Instalment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Schedule(UUID offerId, BigDecimal principal, BigDecimal annualRate, BigDecimal emi,
                       BigDecimal totalInterest, List<Instalment> instalments) {
}
