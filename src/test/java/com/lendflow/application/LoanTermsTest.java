package com.lendflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.application.domain.EmploymentType;
import com.lendflow.application.domain.LoanTerms;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoanTermsTest {

    @Test
    void storesRupeesWithTwoDecimals() {
        LoanTerms terms = new LoanTerms(new BigDecimal("500000"), 36, new BigDecimal("85000.5"), BigDecimal.ZERO,
            EmploymentType.SALARIED, 30, null);

        assertThat(terms.amount()).isEqualByComparingTo("500000.00");
        assertThat(terms.amount().scale()).isEqualTo(2);
        assertThat(terms.missing(false)).isEmpty();
        assertThat(terms.missing(true)).containsExactly("propertyValue");
    }

    @Test
    void rejectsPaiseFractionsAndNegativeValues() {
        assertThatThrownBy(() -> new LoanTerms(new BigDecimal("1000.005"), null, null, null, null, null, null))
            .hasMessageContaining("two decimal places");
        assertThatThrownBy(() -> new LoanTerms(null, null, null, new BigDecimal("-1"), null, null, null))
            .hasMessageContaining("existingEmi");
        assertThatThrownBy(() -> new LoanTerms(BigDecimal.ZERO, null, null, null, null, null, null))
            .hasMessageContaining("positive");
        assertThatThrownBy(() -> new LoanTerms(null, 2, null, null, null, null, null))
            .hasMessageContaining("tenureMonths");
    }

    @Test
    void mergeOverwritesOnlyWhatThePatchSets() {
        LoanTerms draft = new LoanTerms(new BigDecimal("500000"), 36, null, null, null, null, null);
        LoanTerms merged = draft.merge(new LoanTerms(null, 48, new BigDecimal("90000"), null, null, null, null));

        assertThat(merged.amount()).isEqualByComparingTo("500000");
        assertThat(merged.tenureMonths()).isEqualTo(48);
        assertThat(merged.missing(false)).containsExactly("existingEmi", "employmentType", "monthsInCurrentJob");
    }
}
