package com.lendflow.application.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The financial side of a draft. Fields stay null until the applicant fills them in; {@link #missing} says
 * what still blocks submission. Product limits (minimum amount, maximum tenure) live in the ruleset, not here.
 */
public record LoanTerms(BigDecimal amount, Integer tenureMonths, BigDecimal monthlyIncome, BigDecimal existingEmi,
                        EmploymentType employmentType, Integer monthsInCurrentJob, BigDecimal propertyValue) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");

    public static final LoanTerms EMPTY = new LoanTerms(null, null, null, null, null, null, null);

    public LoanTerms {
        amount = rupees(amount, "amount", false);
        monthlyIncome = rupees(monthlyIncome, "monthlyIncome", false);
        existingEmi = rupees(existingEmi, "existingEmi", true);
        propertyValue = rupees(propertyValue, "propertyValue", false);
        if (tenureMonths != null && (tenureMonths < 3 || tenureMonths > 360)) {
            throw new IllegalArgumentException("tenureMonths must be 3-360");
        }
        if (monthsInCurrentJob != null && (monthsInCurrentJob < 0 || monthsInCurrentJob > 600)) {
            throw new IllegalArgumentException("monthsInCurrentJob must be 0-600");
        }
    }

    public LoanTerms merge(LoanTerms patch) {
        return new LoanTerms(
            patch.amount != null ? patch.amount : amount,
            patch.tenureMonths != null ? patch.tenureMonths : tenureMonths,
            patch.monthlyIncome != null ? patch.monthlyIncome : monthlyIncome,
            patch.existingEmi != null ? patch.existingEmi : existingEmi,
            patch.employmentType != null ? patch.employmentType : employmentType,
            patch.monthsInCurrentJob != null ? patch.monthsInCurrentJob : monthsInCurrentJob,
            patch.propertyValue != null ? patch.propertyValue : propertyValue);
    }

    public List<String> missing(boolean secured) {
        List<String> missing = new ArrayList<>();
        if (amount == null) {
            missing.add("amount");
        }
        if (tenureMonths == null) {
            missing.add("tenureMonths");
        }
        if (monthlyIncome == null) {
            missing.add("monthlyIncome");
        }
        if (existingEmi == null) {
            missing.add("existingEmi");
        }
        if (employmentType == null) {
            missing.add("employmentType");
        }
        if (monthsInCurrentJob == null) {
            missing.add("monthsInCurrentJob");
        }
        if (secured && propertyValue == null) {
            missing.add("propertyValue");
        }
        return missing;
    }

    private static BigDecimal rupees(BigDecimal value, String field, boolean zeroAllowed) {
        if (value == null) {
            return null;
        }
        if (value.scale() > 2 && value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + " has more than two decimal places");
        }
        int sign = value.signum();
        if (sign < 0 || (sign == 0 && !zeroAllowed)) {
            throw new IllegalArgumentException(field + " must be " + (zeroAllowed ? "zero or more" : "positive"));
        }
        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(field + " is above 10 crore");
        }
        return value.setScale(2);
    }
}
