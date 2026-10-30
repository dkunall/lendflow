package com.lendflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lendflow.application.domain.ApplicationLifecycle;
import com.lendflow.application.domain.ApplicationStatus;
import com.lendflow.application.domain.IllegalTransitionException;
import com.lendflow.application.domain.LifecycleEvent;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationLifecycleTest {

    @Test
    void theHappyPathReachesDisbursed() {
        ApplicationStatus status = ApplicationStatus.DRAFT;
        for (LifecycleEvent event : new LifecycleEvent[] {LifecycleEvent.SUBMIT, LifecycleEvent.CHECKS_COMPLETED,
            LifecycleEvent.AUTO_APPROVE, LifecycleEvent.OFFER_ACCEPTED, LifecycleEvent.FUNDS_SENT}) {
            status = ApplicationLifecycle.require(status, event);
        }
        assertThat(status).isEqualTo(ApplicationStatus.DISBURSED);
    }

    @Test
    void aReferralEndsInApprovalOrDecline() {
        assertThat(ApplicationLifecycle.next(ApplicationStatus.REFER, LifecycleEvent.CHECKER_APPROVE))
            .contains(ApplicationStatus.APPROVED);
        assertThat(ApplicationLifecycle.next(ApplicationStatus.REFER, LifecycleEvent.MANUAL_DECLINE))
            .contains(ApplicationStatus.REJECTED);
    }

    @Test
    void terminalStatusesAcceptNoEvents() {
        for (ApplicationStatus terminal : EnumSet.of(ApplicationStatus.REJECTED, ApplicationStatus.DISBURSED,
            ApplicationStatus.WITHDRAWN)) {
            assertThat(terminal.isTerminal()).isTrue();
            for (LifecycleEvent event : LifecycleEvent.values()) {
                assertThat(ApplicationLifecycle.next(terminal, event)).as(terminal + " + " + event).isEmpty();
            }
        }
    }

    @Test
    void theTableHasExactlyTheDocumentedTransitions() {
        int allowed = 0;
        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (LifecycleEvent event : LifecycleEvent.values()) {
                if (ApplicationLifecycle.next(from, event).isPresent()) {
                    allowed++;
                }
            }
        }
        assertThat(allowed).isEqualTo(11);
    }

    @Test
    void withdrawingIsOnlyPossibleBeforeSubmitOrBeforeAcceptingTheOffer() {
        Set<ApplicationStatus> withdrawable = EnumSet.noneOf(ApplicationStatus.class);
        for (ApplicationStatus from : ApplicationStatus.values()) {
            if (ApplicationLifecycle.next(from, LifecycleEvent.WITHDRAW).isPresent()) {
                withdrawable.add(from);
            }
        }
        assertThat(withdrawable).containsExactlyInAnyOrder(ApplicationStatus.DRAFT, ApplicationStatus.APPROVED);
    }

    @Test
    void anIllegalEventNamesTheStatusAndEvent() {
        assertThatThrownBy(() -> ApplicationLifecycle.require(ApplicationStatus.DRAFT, LifecycleEvent.FUNDS_SENT))
            .isInstanceOf(IllegalTransitionException.class)
            .hasMessageContaining("FUNDS_SENT").hasMessageContaining("DRAFT");
    }
}
