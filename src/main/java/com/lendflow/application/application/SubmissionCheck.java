package com.lendflow.application.application;

import com.lendflow.application.domain.LoanApplication;
import java.util.List;

/**
 * Something outside this module that must be satisfied before submit, such as required documents. Modules
 * further along the flow implement it, so this module does not depend on them.
 */
public interface SubmissionCheck {

    List<String> missing(LoanApplication application);
}
