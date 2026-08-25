package com.hireflow.application.transition;

import com.hireflow.user.entity.Role;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustively checks every (currentStatus, targetStatus, actorRole) combination -
 * 7 statuses x 7 statuses x 3 roles = 147 cases - against a hand-written set of the
 * only transitions the PRD declares legal. This set is derived directly from the spec,
 * not from {@link TransitionValidator}'s implementation, so this test cannot become a
 * tautological restatement of the code under test: if the implementation and this
 * hard-coded legal set ever disagree on a single one of the 147 combinations, the test
 * fails and names exactly which combination broke.
 */
class TransitionValidatorExhaustiveTest {

    private static final TransitionValidator VALIDATOR = new TransitionValidator();

    /** The complete, exhaustive set of legal (current, target, actorRole) triples per the PRD. */
    private static final Set<Transition> LEGAL_TRANSITIONS = Set.of(
            // Forward progression, RECRUITER only, one step at a time.
            new Transition(ApplicationStatus.APPLIED, ApplicationStatus.SCREENING, Role.RECRUITER),
            new Transition(ApplicationStatus.SCREENING, ApplicationStatus.INTERVIEW, Role.RECRUITER),
            new Transition(ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER, Role.RECRUITER),

            // Accept, CANDIDATE only, only from OFFER.
            new Transition(ApplicationStatus.OFFER, ApplicationStatus.ACCEPTED, Role.CANDIDATE),

            // Reject, RECRUITER only, from any non-terminal state.
            new Transition(ApplicationStatus.APPLIED, ApplicationStatus.REJECTED, Role.RECRUITER),
            new Transition(ApplicationStatus.SCREENING, ApplicationStatus.REJECTED, Role.RECRUITER),
            new Transition(ApplicationStatus.INTERVIEW, ApplicationStatus.REJECTED, Role.RECRUITER),
            new Transition(ApplicationStatus.OFFER, ApplicationStatus.REJECTED, Role.RECRUITER),

            // Withdraw, CANDIDATE only, from any non-terminal state.
            new Transition(ApplicationStatus.APPLIED, ApplicationStatus.WITHDRAWN, Role.CANDIDATE),
            new Transition(ApplicationStatus.SCREENING, ApplicationStatus.WITHDRAWN, Role.CANDIDATE),
            new Transition(ApplicationStatus.INTERVIEW, ApplicationStatus.WITHDRAWN, Role.CANDIDATE),
            new Transition(ApplicationStatus.OFFER, ApplicationStatus.WITHDRAWN, Role.CANDIDATE)
    );

    private record Transition(ApplicationStatus current, ApplicationStatus target, Role role) {
    }

    static Stream<Arguments> allCombinations() {
        List<Arguments> args = new ArrayList<>();
        for (ApplicationStatus current : ApplicationStatus.values()) {
            for (ApplicationStatus target : ApplicationStatus.values()) {
                for (Role role : Role.values()) {
                    args.add(Arguments.of(current, target, role));
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "[{index}] {0} -> {1} as {2}")
    @MethodSource("allCombinations")
    void everyCombinationMatchesTheSpec(ApplicationStatus current, ApplicationStatus target, Role role) {
        boolean expectedAllowed = LEGAL_TRANSITIONS.contains(new Transition(current, target, role));

        TransitionResult result = VALIDATOR.validate(current, target, role);

        assertEquals(expectedAllowed, result.allowed(),
                () -> String.format("%s -> %s as %s: expected allowed=%s but got allowed=%s (reason=%s)",
                        current, target, role, expectedAllowed, result.allowed(), result.reason()));

        if (expectedAllowed) {
            assertNull(result.reason(), "an allowed result must not carry a denial reason");
        } else {
            assertTrue(result.reason() != null && !result.reason().isBlank(),
                    "a denied result must carry a human-readable reason");
        }
    }

    @org.junit.jupiter.api.Test
    void exactlyTwelveOfTheOneHundredFortySevenCombinationsAreLegal() {
        assertEquals(12, LEGAL_TRANSITIONS.size());

        long allowedCount = allCombinations()
                .map(args -> args.get())
                .filter(a -> VALIDATOR.validate((ApplicationStatus) a[0], (ApplicationStatus) a[1], (Role) a[2]).allowed())
                .count();

        assertEquals(12, allowedCount);
    }

    @org.junit.jupiter.api.Test
    void adminIsNeverAllowedAnyTransition() {
        for (ApplicationStatus current : ApplicationStatus.values()) {
            for (ApplicationStatus target : ApplicationStatus.values()) {
                if (current == target) {
                    continue;
                }
                assertFalse(VALIDATOR.validate(current, target, Role.ADMIN).allowed(),
                        () -> "ADMIN should never be allowed to transition " + current + " -> " + target);
            }
        }
    }
}
