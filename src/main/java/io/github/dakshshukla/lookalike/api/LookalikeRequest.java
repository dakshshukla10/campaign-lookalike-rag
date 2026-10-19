package io.github.dakshshukla.lookalike.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/** Request body for {@code POST /v1/lookalikes}. */
public record LookalikeRequest(
        @NotBlank String description,
        String category,
        String audience,
        String channel,
        String objective,
        @Positive Integer budgetUsd) {

    public CampaignBrief toBrief() {
        return new CampaignBrief(description, category, audience, channel, objective, budgetUsd);
    }
}
