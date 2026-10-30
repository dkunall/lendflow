package com.lendflow.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.shared.domain.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-30T06:30:00Z");
    private static final BigDecimal GST = new BigDecimal("18");

    private final OfferGenerator personal = new PersonalOfferGenerator(GST, new BigDecimal("1000"), Duration.ofDays(7));
    private final OfferGenerator home = new HomeOfferGenerator(GST, new BigDecimal("25000"), Duration.ofDays(30));

    @Test
    void personalLoanFeeAndItsTaxComeOutOfTheDisbursal() {
        Offer offer = personal.generate(UUID.randomUUID(), UUID.randomUUID(),
            new OfferTerms(Product.PERSONAL, new BigDecimal("300000"), new BigDecimal("10.50"), 36, new BigDecimal("1.0")), NOW);

        assertThat(offer.processingFee()).isEqualByComparingTo("3000.00");
        assertThat(offer.feeGst()).isEqualByComparingTo("540.00");
        assertThat(offer.upfrontCharges()).isEqualByComparingTo("0");
        assertThat(offer.netDisbursal()).isEqualByComparingTo("296460.00");
        assertThat(offer.emi()).isEqualByComparingTo("9750.73");
        assertThat(offer.totalInterest()).isEqualByComparingTo("51026.39");
        assertThat(offer.totalRepayable()).isEqualByComparingTo("351026.39");
        assertThat(offer.validUntil()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(offer.status()).isEqualTo(OfferStatus.OFFERED);
    }

    @Test
    void smallPersonalLoansPayTheMinimumFee() {
        Offer offer = personal.generate(UUID.randomUUID(), UUID.randomUUID(),
            new OfferTerms(Product.PERSONAL, new BigDecimal("50000"), new BigDecimal("16.00"), 12, new BigDecimal("1.0")), NOW);

        assertThat(offer.processingFee()).isEqualByComparingTo("1000.00");
        assertThat(offer.feeGst()).isEqualByComparingTo("180.00");
        assertThat(offer.netDisbursal()).isEqualByComparingTo("48820.00");
    }

    @Test
    void homeLoanFeeIsCappedAndPaidUpfrontSoTheFullAmountIsDisbursed() {
        Offer offer = home.generate(UUID.randomUUID(), UUID.randomUUID(),
            new OfferTerms(Product.HOME, new BigDecimal("5600000"), new BigDecimal("8.50"), 240, new BigDecimal("0.50")), NOW);

        assertThat(offer.processingFee()).isEqualByComparingTo("25000.00");
        assertThat(offer.feeGst()).isEqualByComparingTo("4500.00");
        assertThat(offer.upfrontCharges()).isEqualByComparingTo("29500.00");
        assertThat(offer.netDisbursal()).isEqualByComparingTo("5600000.00");
        assertThat(offer.emi()).isEqualByComparingTo("48598.10");
        assertThat(offer.totalInterest()).isEqualByComparingTo("6063544.61");
    }

    @Test
    void anOfferNobodyAcceptedInTimeReadsAsExpired() {
        Offer offer = personal.generate(UUID.randomUUID(), UUID.randomUUID(),
            new OfferTerms(Product.PERSONAL, new BigDecimal("100000"), new BigDecimal("12.00"), 24, new BigDecimal("1.0")), NOW);

        assertThat(offer.statusAt(NOW.plus(Duration.ofDays(7)))).isEqualTo(OfferStatus.OFFERED);
        assertThat(offer.statusAt(NOW.plus(Duration.ofDays(7)).plusSeconds(1))).isEqualTo(OfferStatus.EXPIRED);
        assertThat(offer.accepted(NOW, "someone").statusAt(NOW.plus(Duration.ofDays(90)))).isEqualTo(OfferStatus.ACCEPTED);
    }

    @Test
    void refusesTermsForAnotherProduct() {
        assertThatThrownBy(() -> home.generate(UUID.randomUUID(), UUID.randomUUID(),
            new OfferTerms(Product.PERSONAL, new BigDecimal("100000"), new BigDecimal("12.00"), 24, BigDecimal.ONE), NOW))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
