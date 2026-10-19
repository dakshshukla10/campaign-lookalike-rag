package io.github.dakshshukla.lookalike.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/** Request body for {@code POST /v1/lookalikes}. */
public record LookalikeRequest(
        @NotBlank String description,
        @Pattern(regexp = SLUG, message = "letters, digits, space, - and _ only") String category,
        @Pattern(regexp = SLUG, message = "letters, digits, space, - and _ only") String audience,
        @Pattern(regexp = SLUG, message = "letters, digits, space, - and _ only") String channel,
        @Pattern(regexp = SLUG, message = "letters, digits, space, - and _ only") String objective,
        @Positive Integer budgetUsd) {

    private static final String SLUG = "^[A-Za-z0-9 _-]{0,64}$";

    public CampaignBrief toBrief() {
        return new CampaignBrief(description, category, audience, channel, objective, budgetUsd);
    }
}
