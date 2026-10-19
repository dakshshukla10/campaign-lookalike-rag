package io.github.dakshshukla.lookalike.feedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Report for {@code POST /v1/outcomes}: the past campaign that was recommended and whether it
 * turned out to be a useful comparison for the new campaign.
 */
public record OutcomeRequest(@NotBlank String campaignId, @NotNull Boolean helpful) {
}
