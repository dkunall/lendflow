package com.lendflow.underwriting.application;

/** @param action APPROVE or DECLINE from a maker; CONFIRM or SEND_BACK from a checker */
public record TaskCompletion(String action, String note) {
}
