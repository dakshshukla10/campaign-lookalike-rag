package io.github.dakshshukla.lookalike.grounding;

/** One reason a recommendation was rejected by the {@link GroundingValidator}. */
public record Violation(int recommendationRank, String citedId, Reason reason) {

    public enum Reason {
        /** The recommendation has no citations at all. */
        NO_CITATIONS,
        /** A citation has a null or blank campaign id. */
        BLANK_ID,
        /** The cited campaign id was not in the retrieved set. */
        NOT_RETRIEVED
    }
}
