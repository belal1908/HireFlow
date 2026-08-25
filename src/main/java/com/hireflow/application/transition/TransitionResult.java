package com.hireflow.application.transition;

/**
 * Result of a transition validation attempt. Deliberately not a plain boolean: callers
 * (services, controllers, tests) need to surface *why* a transition was denied.
 */
public record TransitionResult(boolean allowed, String reason) {

    public TransitionResult {
        if (allowed && reason != null) {
            throw new IllegalArgumentException("An allowed TransitionResult must not carry a denial reason");
        }
        if (!allowed && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A denied TransitionResult must carry a non-blank reason");
        }
    }

    public static TransitionResult allow() {
        return new TransitionResult(true, null);
    }

    public static TransitionResult deny(String reason) {
        return new TransitionResult(false, reason);
    }
}
