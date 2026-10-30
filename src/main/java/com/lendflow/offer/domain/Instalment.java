package com.lendflow.offer.domain;

import java.math.BigDecimal;

public record Instalment(int number, BigDecimal opening, BigDecimal payment, BigDecimal interest, BigDecimal principal,
                         BigDecimal closing) {
}
