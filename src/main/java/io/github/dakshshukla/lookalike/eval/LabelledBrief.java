package io.github.dakshshukla.lookalike.eval;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/** A brief with the campaign ids that a good answer should contain. */
public record LabelledBrief(
        String id,
        String description,
        String category,
        String audience,
        String channel,
        String objective,
        Integer budgetUsd,
        List<String> expected) {

    public LabelledBrief {
        expected = expected == null ? List.of() : List.copyOf(expected);
    }

    public CampaignBrief toBrief() {
        return new CampaignBrief(description, category, audience, channel, objective, budgetUsd);
    }

    public boolean hasFilters() {
        return category != null || audience != null || channel != null || objective != null;
    }
}
