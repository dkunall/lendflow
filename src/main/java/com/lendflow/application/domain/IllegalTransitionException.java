package com.lendflow.application.domain;

import com.lendflow.shared.error.StateConflictException;
import java.util.Map;

public class IllegalTransitionException extends StateConflictException {

    public IllegalTransitionException(ApplicationStatus from, LifecycleEvent event) {
        super("illegal-transition", "Cannot " + event + " an application that is " + from,
            Map.of("status", from.name(), "event", event.name()));
    }
}
