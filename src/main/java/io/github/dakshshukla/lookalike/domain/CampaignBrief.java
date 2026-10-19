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

    /** The text that gets embedded. Filter values are included so they also nudge the ranking. */
    public String searchText() {
        StringBuilder text = new StringBuilder(description == null ? "" : description.strip());
        append(text, "Category", category);
        append(text, "Audience", audience);
        append(text, "Channel", channel);
        append(text, "Objective", objective);
        return text.toString();
    }

    private static void append(StringBuilder text, String label, String value) {
        if (value != null && !value.isBlank()) {
            text.append(". ").append(label).append(' ').append(value.strip());
        }
    }
}
