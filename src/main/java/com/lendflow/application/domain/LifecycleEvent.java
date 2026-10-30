package com.lendflow.application.domain;

/**
 * What moves an application. Named for the cause rather than the target state, so the history reads as a
 * story: SUBMIT, CHECKS_COMPLETED, REFER, CHECKER_APPROVE, OFFER_ACCEPTED.
 */
public enum LifecycleEvent {
    SUBMIT, CHECKS_COMPLETED, AUTO_APPROVE, REFER, AUTO_REJECT, CHECKER_APPROVE, MANUAL_DECLINE,
    OFFER_ACCEPTED, FUNDS_SENT, WITHDRAW
}
