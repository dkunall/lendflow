package com.lendflow.application.adapter.web;

import com.lendflow.applicant.domain.PersonalDetails;
import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.application.ApplicationView;
import com.lendflow.application.application.DraftInput;
import com.lendflow.application.application.HistoryEntry;
import com.lendflow.application.domain.EmploymentType;
import com.lendflow.application.domain.LoanTerms;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.domain.Product;
import com.lendflow.shared.error.InvalidRequestException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/applications")
class ApplicationController {

    private final ApplicationService applications;

    ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @PostMapping
    ResponseEntity<ApplicationView> create(Actor actor, @RequestBody DraftRequest body) {
        ApplicationView view = applications.create(actor, body.toInput());
        return ResponseEntity.created(URI.create("/v1/applications/" + view.id())).eTag(etag(view)).body(view);
    }

    @PatchMapping("/{id}")
    ResponseEntity<ApplicationView> update(Actor actor, @PathVariable UUID id,
                                           @RequestHeader(name = "If-Match", required = false) String ifMatch,
                                           @RequestBody DraftRequest body) {
        ApplicationView view = applications.update(actor, id, parseVersion(ifMatch), body.toInput());
        return ResponseEntity.ok().eTag(etag(view)).body(view);
    }

    @PostMapping("/{id}/submit")
    ResponseEntity<ApplicationView> submit(Actor actor, @PathVariable UUID id) {
        ApplicationView view = applications.submit(actor, id);
        return ResponseEntity.ok().eTag(etag(view)).body(view);
    }

    @PostMapping("/{id}/withdraw")
    ApplicationView withdraw(Actor actor, @PathVariable UUID id) {
        return applications.withdraw(actor, id);
    }

    @GetMapping("/{id}")
    ResponseEntity<ApplicationView> get(Actor actor, @PathVariable UUID id) {
        ApplicationView view = applications.get(actor, id);
        return ResponseEntity.ok().eTag(etag(view)).body(view);
    }

    @GetMapping
    List<ApplicationView> mine(Actor actor) {
        return applications.mine(actor);
    }

    @GetMapping("/{id}/history")
    List<HistoryEntry> history(Actor actor, @PathVariable UUID id) {
        return applications.history(actor, id);
    }

    private static String etag(ApplicationView view) {
        return "\"" + view.version() + "\"";
    }

    private static int parseVersion(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new InvalidRequestException("if-match-required", "Send If-Match with the ETag from your last read");
        }
        String raw = ifMatch.trim();
        if (raw.startsWith("W/")) {
            raw = raw.substring(2);
        }
        raw = raw.replace("\"", "");
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("if-match-invalid", "If-Match must be an ETag returned by this API");
        }
    }

    record ApplicantRequest(String fullName, String pan, LocalDate dateOfBirth, String phone, String email) {
    }

    record ConsentRequest(Boolean bureauPull) {
    }

    record DraftRequest(Product product, BigDecimal amount, Integer tenureMonths, BigDecimal monthlyIncome,
                        BigDecimal existingEmi, EmploymentType employmentType, Integer monthsInCurrentJob,
                        BigDecimal propertyValue, ApplicantRequest applicant, ConsentRequest consent) {

        DraftInput toInput() {
            try {
                LoanTerms terms = new LoanTerms(amount, tenureMonths, monthlyIncome, existingEmi, employmentType,
                    monthsInCurrentJob, propertyValue);
                PersonalDetails details = applicant == null ? null : new PersonalDetails(applicant.fullName(),
                    applicant.pan(), applicant.dateOfBirth(), applicant.phone(), applicant.email());
                return new DraftInput(product, terms, details, consent == null ? null : consent.bureauPull());
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("invalid-field", e.getMessage());
            }
        }
    }
}
