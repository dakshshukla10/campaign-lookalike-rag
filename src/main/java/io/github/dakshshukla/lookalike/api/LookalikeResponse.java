package io.github.dakshshukla.lookalike.api;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.grounding.Violation;

/**
 * Response body for {@code POST /v1/lookalikes}. This is the final JSON shape; in Phase 1 the
 * {@code recommendations} list is always empty.
 */
public record LookalikeResponse(
        String status,
        int retrievedCount,
        List<Recommendation> recommendations,
        Grounding grounding,
        List<String> notes) {

    /** Summary of grounding validation for this response. */
    public record Grounding(int citationsChecked, int citationsGrounded, List<Violation> rejected) {
    }
}
