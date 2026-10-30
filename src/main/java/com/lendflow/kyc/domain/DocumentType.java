package com.lendflow.kyc.domain;

import com.lendflow.shared.domain.Product;
import java.util.EnumSet;
import java.util.Set;

public enum DocumentType {
    PAN_CARD, INCOME_PROOF, PROPERTY_DOCUMENT;

    public static Set<DocumentType> requiredFor(Product product) {
        return product.isSecured() ? EnumSet.allOf(DocumentType.class) : EnumSet.of(PAN_CARD, INCOME_PROOF);
    }
}
