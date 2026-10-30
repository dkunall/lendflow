package com.lendflow.underwriting.adapter.web;

import com.lendflow.shared.domain.Actor;
import com.lendflow.underwriting.application.TaskCompletion;
import com.lendflow.underwriting.application.UnderwritingService;
import com.lendflow.underwriting.domain.Task;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TaskController {

    private final UnderwritingService underwriting;

    TaskController(UnderwritingService underwriting) {
        this.underwriting = underwriting;
    }

    @GetMapping("/v1/tasks")
    List<Task> tasks(Actor actor, @RequestParam(required = false) UUID application) {
        return application == null ? underwriting.queue(actor) : underwriting.forApplication(application);
    }

    @PostMapping("/v1/tasks/{id}/claim")
    Task claim(Actor actor, @PathVariable UUID id) {
        return underwriting.claim(actor, id);
    }

    @PostMapping("/v1/tasks/{id}/complete")
    Task complete(Actor actor, @PathVariable UUID id, @RequestBody(required = false) TaskCompletion completion) {
        return underwriting.complete(actor, id, completion);
    }
}
