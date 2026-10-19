package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.Violation;

/**
 * Picks and explains the top lookalikes as structured output, parsed into
 * {@link Recommendation}s with citations.
 */
public interface RecommendationRanker {

    List<Recommendation> rank(CampaignBrief brief, List<ScoredCampaign> rerankedCandidates, int limit);

    /**
     * Second attempt after the grounding validator rejected part of the first answer. The
     * default ignores the violations and asks again.
     */
    default List<Recommendation> retry(
            CampaignBrief brief, List<ScoredCampaign> rerankedCandidates, int limit, List<Violation> violations) {
        return rank(brief, rerankedCandidates, limit);
    }
}
