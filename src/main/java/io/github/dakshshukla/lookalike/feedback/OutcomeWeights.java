package io.github.dakshshukla.lookalike.feedback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/**
 * Turns recorded outcomes into a score multiplier. A campaign with a perfect record gets
 * {@code 1 + strength}, one that was always unhelpful gets {@code 1 - strength}, and one with no
 * reports keeps its score. Strength is small on purpose: feedback should break near ties, not
 * overrule the similarity search.
 */
public final class OutcomeWeights {

    private OutcomeWeights() {
    }

    public static double multiplier(OutcomeStats stats, double strength) {
        return stats == null ? 1.0 : 1.0 + strength * stats.signal();
    }

    /** Re-scores and re-sorts candidates, best first. Equal scores keep their incoming order. */
    public static List<ScoredCampaign> apply(
            List<ScoredCampaign> candidates, Map<String, OutcomeStats> stats, double strength) {
        List<ScoredCampaign> adjusted = new ArrayList<>();
        for (ScoredCampaign candidate : candidates) {
            adjusted.add(candidate.withScore(candidate.score() * multiplier(stats.get(candidate.id()), strength)));
        }
        adjusted.sort((a, b) -> Double.compare(b.score(), a.score()));
        return adjusted;
    }
}
