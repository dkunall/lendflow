package com.lendflow.offer.application;

import com.lendflow.offer.domain.Offer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class SanctionLetter {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private SanctionLetter() {
    }

    static String render(Offer o, String name) {
        String product = o.product().name().charAt(0) + o.product().name().substring(1).toLowerCase(Locale.ROOT);
        StringBuilder letter = new StringBuilder()
            .append("Sanction letter\n")
            .append("Reference: ").append(o.id()).append('\n')
            .append("Date: ").append(DATE.format(o.acceptedAt().atZone(INDIA))).append("\n\n")
            .append("Dear ").append(name).append(",\n\n")
            .append("Your ").append(product.toLowerCase(Locale.ROOT)).append(" loan application ").append(o.applicationId())
            .append(" is sanctioned on the following terms.\n\n")
            .append(line("Loan amount", rupees(o.amount())))
            .append(line("Interest rate", o.annualRate().toPlainString() + "% a year, reducing balance"))
            .append(line("Tenure", o.tenureMonths() + " months"))
            .append(line("Monthly instalment", rupees(o.emi())))
            .append(line("Processing fee", rupees(o.processingFee()) + " + GST " + rupees(o.feeGst())));
        if (o.upfrontCharges().signum() > 0) {
            letter.append(line("Payable before disbursement", rupees(o.upfrontCharges())));
        }
        return letter
            .append(line("Amount to be disbursed", rupees(o.netDisbursal())))
            .append(line("Total interest", rupees(o.totalInterest())))
            .append(line("Total repayable", rupees(o.totalRepayable())))
            .append("\nThe last instalment may differ from the others by a few paise to settle rounding.\n")
            .toString();
    }

    private static String line(String label, String value) {
        return String.format("%-28s %s%n", label + ":", value);
    }

    /**
     * Groups in lakhs and crores. The JDK's en-IN locale data has that pattern, but DecimalFormat only
     * supports one grouping size and prints 300,000.00.
     */
    static String rupees(BigDecimal amount) {
        String plain = amount.setScale(2, RoundingMode.HALF_UP).abs().toPlainString();
        String whole = plain.substring(0, plain.length() - 3);
        StringBuilder grouped = new StringBuilder(whole.substring(Math.max(0, whole.length() - 3)));
        for (int end = whole.length() - 3; end > 0; end -= 2) {
            grouped.insert(0, ',').insert(0, whole.substring(Math.max(0, end - 2), end));
        }
        return "INR " + (amount.signum() < 0 ? "-" : "") + grouped + plain.substring(plain.length() - 3);
    }
}
