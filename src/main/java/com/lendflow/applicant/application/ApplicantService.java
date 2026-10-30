package com.lendflow.applicant.application;

import com.lendflow.applicant.domain.DuplicateSignal;
import com.lendflow.applicant.domain.JaroWinkler;
import com.lendflow.applicant.domain.PersonalDetails;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantService {

    private final ApplicantRepository repository;
    private final PiiProtector protector;
    private final Clock clock;
    private final double nameThreshold;
    private final Duration lookback;

    ApplicantService(ApplicantRepository repository, PiiProtector protector, Clock clock,
                     @Value("${lendflow.dedupe.name-threshold:0.92}") double nameThreshold,
                     @Value("${lendflow.dedupe.lookback:P180D}") Duration lookback) {
        this.repository = repository;
        this.protector = protector;
        this.clock = clock;
        this.nameThreshold = nameThreshold;
        this.lookback = lookback;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UUID applicationId, String ownerSubject, PersonalDetails patch) {
        PersonalDetails merged = details(applicationId).map(d -> d.merge(patch)).orElse(patch);
        repository.upsert(new ApplicantRecord(applicationId, ownerSubject, merged.fullName(),
            merged.fullName() == null ? null : PersonalDetails.comparableName(merged.fullName()),
            merged.dateOfBirth(),
            hash(merged.pan()), encrypt(merged.pan()),
            hash(merged.phone()), encrypt(merged.phone()),
            merged.email() == null ? null : protector.lookupHash(PersonalDetails.emailLookupForm(merged.email())),
            encrypt(merged.email()), null));
    }

    @Transactional(readOnly = true)
    public Optional<PersonalDetails> details(UUID applicationId) {
        return repository.find(applicationId).map(r -> new PersonalDetails(r.fullName(), decrypt(r.panEnc()),
            r.dateOfBirth(), decrypt(r.phoneEnc()), decrypt(r.emailEnc())));
    }

    @Transactional(readOnly = true)
    public Optional<ApplicantView> view(UUID applicationId) {
        return details(applicationId).map(d -> new ApplicantView(d.fullName(),
            d.pan() == null ? null : PersonalDetails.maskPan(d.pan()), d.dateOfBirth(),
            d.phone() == null ? null : PersonalDetails.maskPhone(d.phone()), d.email() != null));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markSubmitted(UUID applicationId) {
        repository.markSubmitted(applicationId, clock.instant());
    }

    /**
     * Other recent applications that look like the same person. Exact matches come from the keyed hashes; the
     * fuzzy name check only runs within the same date of birth, which keeps it to a handful of pairs.
     */
    @Transactional(readOnly = true)
    public List<DuplicateMatch> duplicates(UUID applicationId) {
        ApplicantRecord self = repository.find(applicationId)
            .orElseThrow(() -> new IllegalStateException("No applicant for " + applicationId));
        List<ApplicantRecord> candidates = repository.candidates(applicationId, self.panHmac(), self.phoneHmac(),
            self.emailHmac(), self.dateOfBirth(), clock.instant().minus(lookback));
        List<DuplicateMatch> matches = new ArrayList<>();
        for (ApplicantRecord other : candidates) {
            Set<DuplicateSignal> signals = EnumSet.noneOf(DuplicateSignal.class);
            if (same(self.panHmac(), other.panHmac())) {
                signals.add(DuplicateSignal.SAME_PAN);
            }
            if (same(self.phoneHmac(), other.phoneHmac())) {
                signals.add(DuplicateSignal.SAME_PHONE);
            }
            if (same(self.emailHmac(), other.emailHmac())) {
                signals.add(DuplicateSignal.SAME_EMAIL);
            }
            double similarity = self.comparableName() == null || other.comparableName() == null ? 0.0
                : JaroWinkler.similarity(self.comparableName(), other.comparableName());
            if (similarity >= nameThreshold && self.dateOfBirth() != null && self.dateOfBirth().equals(other.dateOfBirth())) {
                signals.add(DuplicateSignal.SIMILAR_NAME_SAME_DOB);
            }
            if (!signals.isEmpty()) {
                matches.add(new DuplicateMatch(other.applicationId(), signals, similarity));
            }
        }
        matches.sort(Comparator.comparing(DuplicateMatch::otherApplicationId));
        return matches;
    }

    private static boolean same(String a, String b) {
        return a != null && a.equals(b);
    }

    private String hash(String value) {
        return value == null ? null : protector.lookupHash(value);
    }

    private String encrypt(String value) {
        return value == null ? null : protector.encrypt(value);
    }

    private String decrypt(String value) {
        return value == null ? null : protector.decrypt(value);
    }
}
