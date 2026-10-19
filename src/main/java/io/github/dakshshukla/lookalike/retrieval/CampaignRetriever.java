package io.github.dakshshukla.lookalike.retrieval;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/**
 * Finds candidate past campaigns for a brief.
 *
 * <p>TODO (Phase 2): embed the brief, run a pgvector similarity search, and apply metadata
 * filters (category, audience, channel, objective).
 */
public interface CampaignRetriever {

    List<Campaign> retrieve(CampaignBrief brief, int topK);
}
