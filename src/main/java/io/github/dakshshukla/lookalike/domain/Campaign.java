package io.github.dakshshukla.lookalike.domain;

/**
 * A past ad campaign that can be retrieved as a lookalike.
 *
 * <p>Phase 1 only defines the shape. The sample data in {@code data/} is synthetic.
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
        double roas) {
}
