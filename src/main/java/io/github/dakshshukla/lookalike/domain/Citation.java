package io.github.dakshshukla.lookalike.domain;

/**
 * Points a recommendation at a source campaign. {@code campaignId} must be the id of a
 * campaign that was actually retrieved for this request; the grounding validator enforces it.
 */
public record Citation(String campaignId, String reason) {
}
