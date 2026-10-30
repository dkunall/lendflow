package com.lendflow.offer.application;

import com.lendflow.offer.domain.Offer;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository {

    /** @return false if the application already has an offer */
    boolean insertFirst(Offer offer);

    Optional<Offer> find(UUID id);

    Optional<Offer> forApplication(UUID applicationId);

    /** Reads the offer and holds a row lock until the transaction ends. */
    Optional<Offer> lock(UUID id);

    void markAccepted(Offer accepted);
}
