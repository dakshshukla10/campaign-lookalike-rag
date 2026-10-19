package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/**
 * Re-orders retrieved candidates with a stronger relevance model. Implementations return every
 * candidate they were given, best first, with the score that decided the order.
 */
public interface Reranker {

    List<ScoredCampaign> rerank(CampaignBrief brief, List<ScoredCampaign> candidates);
}
