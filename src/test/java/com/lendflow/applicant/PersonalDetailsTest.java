package com.lendflow.applicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.applicant.domain.PersonalDetails;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PersonalDetailsTest {

    @Test
    void normalisesWhatPeopleActuallyType() {
        PersonalDetails details = new PersonalDetails("  Rahul   Verma ", " abcde1234f", LocalDate.of(1994, 5, 2),
            "+91 98765-43210", "Rahul.Verma@Example.com");

        assertThat(details.fullName()).isEqualTo("Rahul Verma");
        assertThat(details.pan()).isEqualTo("ABCDE1234F");
        assertThat(details.phone()).isEqualTo("9876543210");
        assertThat(details.email()).isEqualTo("rahul.verma@example.com");
        assertThat(details.complete()).isTrue();
    }

    @Test
    void acceptsATrunkPrefixOnTheMobileNumber() {
        assertThat(new PersonalDetails(null, null, null, "09876543210", null).phone()).isEqualTo("9876543210");
    }

    @Test
    void rejectsMalformedIdentifiers() {
        assertThatThrownBy(() -> new PersonalDetails(null, "ABCD1234F", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonalDetails(null, null, null, "5876543210", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PersonalDetails(null, null, null, null, "not-an-email"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mergeKeepsFieldsThePatchLeavesOut() {
        PersonalDetails first = new PersonalDetails("Rahul Verma", "ABCDE1234F", null, null, null);
        PersonalDetails merged = first.merge(new PersonalDetails(null, null, LocalDate.of(1994, 5, 2), "9876543210", null));

        assertThat(merged.fullName()).isEqualTo("Rahul Verma");
        assertThat(merged.pan()).isEqualTo("ABCDE1234F");
        assertThat(merged.phone()).isEqualTo("9876543210");
        assertThat(merged.complete()).isFalse();
    }

    @Test
    void comparableNameDropsTitlesAndPunctuation() {
        assertThat(PersonalDetails.comparableName("Dr. Rahul  K. Verma")).isEqualTo("RAHUL K VERMA");
        assertThat(PersonalDetails.comparableName("Smt Lakshmi Iyer")).isEqualTo("LAKSHMI IYER");
    }

    @Test
    void gmailAliasesShareALookupForm() {
        assertThat(PersonalDetails.emailLookupForm("r.verma+loans@gmail.com")).isEqualTo("rverma@gmail.com");
        assertThat(PersonalDetails.emailLookupForm("r.verma@googlemail.com")).isEqualTo("rverma@gmail.com");
        assertThat(PersonalDetails.emailLookupForm("r.verma+x@example.com")).isEqualTo("r.verma+x@example.com");
    }

    @Test
    void masksKeepOnlyWhatAPersonNeedsToRecogniseTheirOwnRecord() {
        assertThat(PersonalDetails.maskPan("ABCDE1234F")).isEqualTo("XXXXX1234F");
        assertThat(PersonalDetails.maskPhone("9876543210")).isEqualTo("XXXXXX3210");
    }
}
