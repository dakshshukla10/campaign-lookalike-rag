package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/**
 * Re-orders retrieved candidates with a stronger relevance model.
 *
 * <p>TODO (Phase 3): cross-encoder reranking of the retrieved candidates.
 */
public interface Reranker {

    List<Campaign> rerank(CampaignBrief brief, List<Campaign> candidates);
}
