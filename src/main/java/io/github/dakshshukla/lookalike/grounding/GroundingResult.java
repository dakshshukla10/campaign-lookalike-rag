package io.github.dakshshukla.lookalike.grounding;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.Recommendation;

/**
 * Outcome of grounding validation.
 *
 * @param accepted         recommendations whose citations all point at retrieved campaigns
 * @param violations       every problem found, including those on rejected recommendations
 * @param citationsChecked number of citations inspected
 * @param citationsGrounded number of those citations that pointed at a retrieved campaign
 */
public record GroundingResult(
        List<Recommendation> accepted,
        List<Violation> violations,
        int citationsChecked,
        int citationsGrounded) {

    public GroundingResult {
        accepted = List.copyOf(accepted);
        violations = List.copyOf(violations);
    }

    public boolean fullyGrounded() {
        return violations.isEmpty();
    }
}
