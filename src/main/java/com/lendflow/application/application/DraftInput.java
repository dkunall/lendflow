package com.lendflow.application.application;

import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.shared.domain.Product;

/**
 * A create or a partial update. Null means "leave as is"; a draft cannot clear a field, only overwrite it.
 */
public record DraftInput(Product product, LoanTerms terms, PersonalDetails applicant, Boolean bureauConsent) {
}
