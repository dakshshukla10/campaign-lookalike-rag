package io.github.dakshshukla.lookalike.domain;

/**
 * The query: a description of a new campaign that has not run yet. The optional fields
 * become metadata filters during retrieval.
 */
public record CampaignBrief(
        String description,
        String category,
        String audience,
        String channel,
        String objective,
        Integer budgetUsd) {
}
