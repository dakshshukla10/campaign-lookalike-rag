package io.github.dakshshukla.lookalike.domain;

/**
 * A past ad campaign that can be retrieved as a lookalike. The data in {@code data/} is
 * synthetic.
 */
public record Campaign(
        String id,
        String name,
        String category,
        String audience,
        String channel,
        String objective,
        int budgetUsd,
        int durationDays,
        long impressions,
        double ctr,
        double roas,
        String description) {

    public Campaign {
        description = description == null ? "" : description;
    }

    /** For callers that only have the structured fields. */
    public Campaign(
            String id,
            String name,
            String category,
            String audience,
            String channel,
            String objective,
            int budgetUsd,
            int durationDays,
            long impressions,
            double ctr,
            double roas) {
        this(id, name, category, audience, channel, objective, budgetUsd, durationDays, impressions, ctr, roas, "");
    }

    /** The text that gets embedded and that the reranker reads. */
    public String searchText() {
        return name + ". " + description + " Category " + category + ". Audience " + audience
                + ". Channel " + channel + ". Objective " + objective + ".";
    }
}
