package com.hireflow.application.transition;

import com.hireflow.user.entity.Role;

/**
 * Enforces the HireFlow application state machine:
 *
 * <pre>
 * APPLIED -&gt; SCREENING -&gt; INTERVIEW -&gt; OFFER -&gt; ACCEPTED
 *                                             \-&gt; REJECTED
 *    WITHDRAWN (candidate-only, from any non-terminal state)
 *    REJECTED  (recruiter-only, from any non-terminal state)
 * </pre>
 *
 * Terminal states (ACCEPTED, REJECTED, WITHDRAWN) have no legal transitions out, for any
 * actor, ever - including ADMIN. ADMIN has no transition rights at all under this state
 * machine: it may only read/report on the pipeline (enforced at the API layer), never move
 * an application through it.
 *
 * <p>This class is deliberately framework-free: no Spring, no JPA, no I/O. It is a pure
 * function of (currentStatus, targetStatus, actorRole) -&gt; TransitionResult, so it can be
 * unit tested in isolation and exhaustively.
 */
public class TransitionValidator {

    /** APPLIED -> SCREENING -> INTERVIEW -> OFFER, in order. One step at a time, no skipping. */
    private static final ApplicationStatus[] FORWARD_PATH = {
            ApplicationStatus.APPLIED,
            ApplicationStatus.SCREENING,
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER
    };

    public TransitionResult validate(ApplicationStatus current, ApplicationStatus target, Role actorRole) {
        if (current == null || target == null || actorRole == null) {
            return TransitionResult.deny("Current status, target status, and actor role are all required");
        }
        if (current == target) {
            return TransitionResult.deny("Target status must differ from the current status (" + current + ")");
        }
        if (current.isTerminal()) {
            return TransitionResult.deny("Application is in terminal state " + current + "; no further transitions are allowed");
        }

        if (target == ApplicationStatus.WITHDRAWN) {
            return validateWithdrawal(actorRole);
        }
        if (target == ApplicationStatus.REJECTED) {
            return validateRejection(actorRole);
        }
        if (target == ApplicationStatus.ACCEPTED) {
            return validateAcceptance(current, actorRole);
        }
        if (isForwardStep(current, target)) {
            return validateForwardStep(target, actorRole);
        }

        return TransitionResult.deny("No transition is defined from " + current + " to " + target);
    }

    private TransitionResult validateWithdrawal(Role actorRole) {
        if (actorRole != Role.CANDIDATE) {
            return TransitionResult.deny("Only the CANDIDATE who owns an application may withdraw it, got " + actorRole);
        }
        return TransitionResult.allow();
    }

    private TransitionResult validateRejection(Role actorRole) {
        if (actorRole != Role.RECRUITER) {
            return TransitionResult.deny("Only a RECRUITER may reject an application, got " + actorRole);
        }
        return TransitionResult.allow();
    }

    private TransitionResult validateAcceptance(ApplicationStatus current, Role actorRole) {
        if (actorRole != Role.CANDIDATE) {
            return TransitionResult.deny("Only the CANDIDATE who owns an application may accept an offer, got " + actorRole);
        }
        if (current != ApplicationStatus.OFFER) {
            return TransitionResult.deny("Can only accept from OFFER, application is currently " + current);
        }
        return TransitionResult.allow();
    }

    private TransitionResult validateForwardStep(ApplicationStatus target, Role actorRole) {
        if (actorRole != Role.RECRUITER) {
            return TransitionResult.deny("Only a RECRUITER may advance an application to " + target + ", got " + actorRole);
        }
        return TransitionResult.allow();
    }

    /** True only if target is exactly one step ahead of current on the forward path. */
    private boolean isForwardStep(ApplicationStatus current, ApplicationStatus target) {
        int currentIndex = indexOfOnForwardPath(current);
        int targetIndex = indexOfOnForwardPath(target);
        return currentIndex >= 0 && targetIndex == currentIndex + 1;
    }

    private int indexOfOnForwardPath(ApplicationStatus status) {
        for (int i = 0; i < FORWARD_PATH.length; i++) {
            if (FORWARD_PATH[i] == status) {
                return i;
            }
        }
        return -1;
    }
}
