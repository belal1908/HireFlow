package com.hireflow.application.transition;

import com.hireflow.user.entity.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One explicit, readable negative test per category of illegal transition called out by the
 * PRD: wrong role, wrong starting state, skipped step, backward step, terminal state, and
 * malformed input. See {@link TransitionValidatorExhaustiveTest} for the exhaustive
 * cross-product check covering every one of the 147 combinations.
 */
class TransitionValidatorNegativeTest {

    private final TransitionValidator validator = new TransitionValidator();

    @ParameterizedTest(name = "{0} -> {1} by {2} is denied (wrong role for forward progression)")
    @CsvSource({
            "APPLIED, SCREENING, CANDIDATE",
            "APPLIED, SCREENING, ADMIN",
            "SCREENING, INTERVIEW, CANDIDATE",
            "SCREENING, INTERVIEW, ADMIN",
            "INTERVIEW, OFFER, CANDIDATE",
            "INTERVIEW, OFFER, ADMIN",
    })
    void wrongRoleForForwardProgressionIsDenied(ApplicationStatus current, ApplicationStatus target, Role role) {
        assertDenied(current, target, role);
    }

    @ParameterizedTest(name = "{0} -> ACCEPTED by {1} is denied (wrong role or wrong starting state)")
    @CsvSource({
            "OFFER, RECRUITER",
            "OFFER, ADMIN",
            "APPLIED, CANDIDATE",
            "SCREENING, CANDIDATE",
            "INTERVIEW, CANDIDATE",
    })
    void acceptOnlyFromOfferByCandidate(ApplicationStatus current, Role role) {
        assertDenied(current, ApplicationStatus.ACCEPTED, role);
    }

    @ParameterizedTest(name = "{0} -> REJECTED by {1} is denied (wrong role)")
    @CsvSource({
            "APPLIED, CANDIDATE",
            "APPLIED, ADMIN",
            "SCREENING, CANDIDATE",
            "INTERVIEW, ADMIN",
            "OFFER, CANDIDATE",
    })
    void rejectByNonRecruiterIsDenied(ApplicationStatus current, Role role) {
        assertDenied(current, ApplicationStatus.REJECTED, role);
    }

    @ParameterizedTest(name = "{0} -> WITHDRAWN by {1} is denied (wrong role)")
    @CsvSource({
            "APPLIED, RECRUITER",
            "APPLIED, ADMIN",
            "SCREENING, RECRUITER",
            "INTERVIEW, ADMIN",
            "OFFER, RECRUITER",
    })
    void withdrawByNonCandidateIsDenied(ApplicationStatus current, Role role) {
        assertDenied(current, ApplicationStatus.WITHDRAWN, role);
    }

    @ParameterizedTest(name = "skipping a step: {0} -> {1} by RECRUITER is denied")
    @CsvSource({
            "APPLIED, INTERVIEW",
            "APPLIED, OFFER",
            "SCREENING, OFFER",
    })
    void skippingAForwardStepIsDenied(ApplicationStatus current, ApplicationStatus target) {
        assertDenied(current, target, Role.RECRUITER);
    }

    @ParameterizedTest(name = "moving backward: {0} -> {1} by RECRUITER is denied")
    @CsvSource({
            "SCREENING, APPLIED",
            "INTERVIEW, SCREENING",
            "OFFER, INTERVIEW",
            "OFFER, APPLIED",
    })
    void movingBackwardIsDenied(ApplicationStatus current, ApplicationStatus target) {
        assertDenied(current, target, Role.RECRUITER);
    }

    @ParameterizedTest(name = "no transitions out of terminal state {0}, ever - not even ADMIN")
    @EnumSource(value = ApplicationStatus.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
    void terminalStatesHaveNoWayOut(ApplicationStatus terminal) {
        for (ApplicationStatus target : ApplicationStatus.values()) {
            if (target == terminal) {
                continue;
            }
            for (Role role : Role.values()) {
                TransitionResult result = validator.validate(terminal, target, role);
                assertFalse(result.allowed(), () -> terminal + " -> " + target + " by " + role + " must be denied (terminal state)");
                assertTrue(result.reason().toLowerCase().contains("terminal"),
                        () -> "denial reason should call out the terminal state, was: " + result.reason());
            }
        }
    }

    @Test
    void selfTransitionIsDenied() {
        for (ApplicationStatus status : ApplicationStatus.values()) {
            for (Role role : Role.values()) {
                assertDenied(status, status, role);
            }
        }
    }

    @Test
    void nullCurrentStatusIsDenied() {
        TransitionResult result = validator.validate(null, ApplicationStatus.SCREENING, Role.RECRUITER);
        assertFalse(result.allowed());
        assertNotNull(result.reason());
    }

    @Test
    void nullTargetStatusIsDenied() {
        TransitionResult result = validator.validate(ApplicationStatus.APPLIED, null, Role.RECRUITER);
        assertFalse(result.allowed());
        assertNotNull(result.reason());
    }

    @Test
    void nullActorRoleIsDenied() {
        TransitionResult result = validator.validate(ApplicationStatus.APPLIED, ApplicationStatus.SCREENING, null);
        assertFalse(result.allowed());
        assertNotNull(result.reason());
    }

    private void assertDenied(ApplicationStatus current, ApplicationStatus target, Role role) {
        TransitionResult result = validator.validate(current, target, role);
        assertFalse(result.allowed(), () -> "expected " + current + " -> " + target + " by " + role + " to be denied");
        assertNotNull(result.reason(), "a denied result must carry a reason");
        assertTrue(!result.reason().isBlank());
    }
}
