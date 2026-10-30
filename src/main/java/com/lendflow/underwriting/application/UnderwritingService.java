package com.lendflow.underwriting.application;

import com.lendflow.application.application.ApplicationService;
import com.lendflow.application.application.ApplicationTransitions;
import com.lendflow.application.domain.LifecycleEvent;
import com.lendflow.application.domain.LoanApplication;
import com.lendflow.decision.application.DecisionService;
import com.lendflow.decision.application.DecisionView;
import com.lendflow.offer.application.OfferService;
import com.lendflow.outbox.application.OutboxWriter;
import com.lendflow.shared.domain.Actor;
import com.lendflow.shared.domain.Actor.Role;
import com.lendflow.shared.domain.Product;
import com.lendflow.shared.error.BusinessRuleException;
import com.lendflow.shared.error.ForbiddenException;
import com.lendflow.shared.error.InvalidRequestException;
import com.lendflow.shared.error.NotFoundException;
import com.lendflow.shared.error.StateConflictException;
import com.lendflow.shared.messaging.Topics;
import com.lendflow.underwriting.domain.ApprovalAuthority;
import com.lendflow.underwriting.domain.Authority;
import com.lendflow.underwriting.domain.Recommendation;
import com.lendflow.underwriting.domain.Task;
import com.lendflow.underwriting.domain.TaskStage;
import com.lendflow.underwriting.domain.TaskStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maker-checker review of referred applications. The maker recommends; a different person with authority for
 * the amount confirms or sends it back. Only a confirmed recommendation changes the application.
 */
@Service
public class UnderwritingService {

    private final TaskRepository tasks;
    private final ApplicationService applications;
    private final ApplicationTransitions transitions;
    private final DecisionService decisions;
    private final OfferService offers;
    private final OutboxWriter outbox;
    private final ApprovalAuthority authority;
    private final Clock clock;
    private final MeterRegistry meters;
    private final Duration makerSla;
    private final Duration checkerSla;

    UnderwritingService(TaskRepository tasks, ApplicationService applications, ApplicationTransitions transitions,
                        DecisionService decisions, OfferService offers, OutboxWriter outbox, Clock clock,
                        MeterRegistry meters,
                        @Value("${lendflow.underwriting.maker-sla:4h}") Duration makerSla,
                        @Value("${lendflow.underwriting.checker-sla:2h}") Duration checkerSla,
                        @Value("${lendflow.underwriting.manager-limit.personal:1500000}") BigDecimal personalLimit,
                        @Value("${lendflow.underwriting.manager-limit.home:10000000}") BigDecimal homeLimit) {
        this.tasks = tasks;
        this.applications = applications;
        this.transitions = transitions;
        this.decisions = decisions;
        this.offers = offers;
        this.outbox = outbox;
        this.clock = clock;
        this.meters = meters;
        this.makerSla = makerSla;
        this.checkerSla = checkerSla;
        this.authority = ApprovalAuthority.chain(Map.of(Product.PERSONAL, personalLimit, Product.HOME, homeLimit));
    }

    /** Puts a referred application in the underwriters' queue. Opening it twice leaves one task. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void open(UUID applicationId) {
        Instant now = clock.instant();
        if (tasks.insertIfNoneActive(Task.maker(UUID.randomUUID(), applicationId, now, now.plus(makerSla)))) {
            meters.counter("lendflow.underwriting.tasks.opened", "stage", TaskStage.MAKER.name()).increment();
        }
    }

    @Transactional(readOnly = true)
    public List<Task> queue(Actor actor) {
        return tasks.queue(actor.subject(), Authority.claimable(actor.roles()), 50);
    }

    @Transactional(readOnly = true)
    public List<Task> forApplication(UUID applicationId) {
        applications.find(applicationId);
        return tasks.forApplication(applicationId);
    }

    @Transactional
    public Task claim(Actor actor, UUID taskId) {
        if (tasks.claim(taskId, actor.subject(), Authority.claimable(actor.roles()), clock.instant())) {
            return tasks.find(taskId).orElseThrow();
        }
        Task task = tasks.find(taskId).orElseThrow(() -> notFound(taskId));
        if (actor.subject().equals(task.maker())) {
            throw new ForbiddenException("four-eyes", "The maker of a recommendation cannot check it");
        }
        if (!Authority.covers(actor.roles(), task.role())) {
            throw new ForbiddenException("insufficient-authority", "This task needs " + task.role());
        }
        if (task.status() == TaskStatus.CLAIMED && actor.subject().equals(task.assignee())) {
            return task;
        }
        throw new StateConflictException("task-not-open", "Task " + taskId + " is " + task.status());
    }

    @Transactional
    public Task complete(Actor actor, UUID taskId, TaskCompletion completion) {
        Task task = tasks.lock(taskId).orElseThrow(() -> notFound(taskId));
        if (task.status() != TaskStatus.CLAIMED || !actor.subject().equals(task.assignee())) {
            throw new StateConflictException("task-not-held", "Claim task " + taskId + " before completing it");
        }
        String note = completion == null || completion.note() == null ? "" : completion.note().trim();
        if (note.isEmpty() || note.length() > 1000) {
            throw new InvalidRequestException("note-required", "A note of up to 1000 characters is required");
        }
        String action = completion.action() == null ? "" : completion.action();
        Instant now = clock.instant();
        tasks.complete(taskId, action, note, now);
        if (task.stage() == TaskStage.MAKER) {
            recommend(task, action, now);
        } else {
            check(task, action, note, now);
        }
        meters.counter("lendflow.underwriting.tasks.completed", "stage", task.stage().name(), "action", action)
            .increment();
        return tasks.find(taskId).orElseThrow();
    }

    private void recommend(Task task, String action, Instant now) {
        Recommendation recommendation = switch (action) {
            case "APPROVE" -> Recommendation.APPROVE;
            case "DECLINE" -> Recommendation.DECLINE;
            default -> throw new InvalidRequestException("invalid-action", "A maker can APPROVE or DECLINE");
        };
        BigDecimal amount = null;
        if (recommendation == Recommendation.APPROVE) {
            amount = decisions.recorded(task.applicationId()).map(DecisionView::approvedAmount).orElseThrow(() ->
                new BusinessRuleException("no-approved-terms",
                    "There are no priced terms to approve because the checks did not complete"));
        }
        LoanApplication application = applications.find(task.applicationId());
        Task checker = task.checker(UUID.randomUUID(), authority.checkerFor(application.product(), amount),
            recommendation, amount, now, now.plus(checkerSla));
        tasks.insertIfNoneActive(checker);
    }

    private void check(Task task, String action, String note, Instant now) {
        switch (action) {
            case "CONFIRM" -> {
                if (task.recommendation() == Recommendation.APPROVE) {
                    transitions.apply(task.applicationId(), LifecycleEvent.CHECKER_APPROVE, task.assignee(), note);
                    offers.create(task.applicationId());
                } else {
                    transitions.apply(task.applicationId(), LifecycleEvent.MANUAL_DECLINE, task.assignee(), note);
                }
                outbox.append(Topics.APPLICATIONS, task.applicationId().toString(), "ManualDecisionMade", Map.of(
                    "applicationId", task.applicationId(), "recommendation", task.recommendation(),
                    "maker", task.maker(), "checker", task.assignee()));
            }
            case "SEND_BACK" ->
                tasks.insertIfNoneActive(Task.maker(UUID.randomUUID(), task.applicationId(), now, now.plus(makerSla)));
            default -> throw new InvalidRequestException("invalid-action", "A checker can CONFIRM or SEND_BACK");
        }
    }

    /**
     * Moves overdue work one rung up and back into the queue. The claim is released on purpose: an overdue
     * task is usually one somebody picked up and left.
     */
    @Transactional
    public int escalateOverdue(int limit) {
        Instant now = clock.instant();
        List<Task> overdue = tasks.lockOverdue(now, limit);
        for (Task task : overdue) {
            Duration sla = task.stage() == TaskStage.MAKER ? makerSla : checkerSla;
            Role role = Authority.above(task.role());
            tasks.escalate(task.id(), role, now.plus(sla));
            outbox.append(Topics.APPLICATIONS, task.applicationId().toString(), "TaskEscalated", Map.of(
                "taskId", task.id(), "stage", task.stage(), "from", task.role(), "to", role,
                "escalations", task.escalations() + 1));
            meters.counter("lendflow.underwriting.tasks.escalated", "stage", task.stage().name()).increment();
        }
        return overdue.size();
    }

    private static NotFoundException notFound(UUID taskId) {
        return new NotFoundException("task-not-found", "No task " + taskId);
    }
}
