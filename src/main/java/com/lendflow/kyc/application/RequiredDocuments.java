package com.lendflow.kyc.application;

import com.lendflow.application.application.SubmissionCheck;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.kyc.domain.DocumentType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class RequiredDocuments implements SubmissionCheck {

    private final DocumentStore store;

    RequiredDocuments(DocumentStore store) {
        this.store = store;
    }

    @Override
    public List<String> missing(LoanApplication application) {
        Set<DocumentType> required = EnumSet.copyOf(DocumentType.requiredFor(application.product()));
        required.removeAll(store.currentTypes(application.id()));
        return required.stream().map(t -> "document:" + t).toList();
    }
}
