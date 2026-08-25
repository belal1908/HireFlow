package com.hireflow.application.transition;

import com.hireflow.user.entity.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One explicit, readable positive test per legal transition declared in the PRD.
 * See {@link TransitionValidatorExhaustiveTest} for the exhaustive cross-product check.
 */
class TransitionValidatorPositiveTest {

    private final TransitionValidator validator = new TransitionValidator();

    @ParameterizedTest(name = "{0} -> {1} by {2} is allowed")
    @CsvSource({
            // Forward progression, one step at a time, RECRUITER only.
            "APPLIED, SCREENING, RECRUITER",
            "SCREENING, INTERVIEW, RECRUITER",
            "INTERVIEW, OFFER, RECRUITER",
            // Accept, CANDIDATE only, only from OFFER.
            "OFFER, ACCEPTED, CANDIDATE",
            // Reject, RECRUITER only, from any non-terminal state.
            "APPLIED, REJECTED, RECRUITER",
            "SCREENING, REJECTED, RECRUITER",
            "INTERVIEW, REJECTED, RECRUITER",
            "OFFER, REJECTED, RECRUITER",
            // Withdraw, CANDIDATE only, from any non-terminal state.
            "APPLIED, WITHDRAWN, CANDIDATE",
            "SCREENING, WITHDRAWN, CANDIDATE",
            "INTERVIEW, WITHDRAWN, CANDIDATE",
            "OFFER, WITHDRAWN, CANDIDATE",
    })
    void legalTransitionIsAllowed(ApplicationStatus current, ApplicationStatus target, Role role) {
        TransitionResult result = validator.validate(current, target, role);

        assertTrue(result.allowed(), () -> "expected " + current + " -> " + target + " by " + role + " to be allowed, reason: " + result.reason());
        assertNull(result.reason());
    }
}
