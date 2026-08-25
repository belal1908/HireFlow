package com.hireflow.application.transition;

/**
 * Every state an {@code Application} can be in. Plain enum, no framework annotations,
 * so it can be shared unmodified between the persistence layer and the framework-free
 * {@link TransitionValidator}.
 */
public enum ApplicationStatus {
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    WITHDRAWN;

    /**
     * Terminal states have no legal transitions out, for any actor, ever - including ADMIN.
     */
    public boolean isTerminal() {
        return this == ACCEPTED || this == REJECTED || this == WITHDRAWN;
    }
}
