package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;

/**
 * Asks an LLM to pick and explain the top lookalikes, returning structured JSON that is parsed
 * into {@link Recommendation}s with citations.
 *
 * <p>TODO (Phase 3): Spring AI chat client with structured output.
 */
public interface RecommendationRanker {

    List<Recommendation> rank(CampaignBrief brief, List<Campaign> rerankedCandidates, int limit);
}
