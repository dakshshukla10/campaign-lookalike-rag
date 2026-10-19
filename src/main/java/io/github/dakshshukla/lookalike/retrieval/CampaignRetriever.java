package io.github.dakshshukla.lookalike.retrieval;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/** Finds candidate past campaigns for a brief, best first. */
public interface CampaignRetriever {

    List<ScoredCampaign> retrieve(CampaignBrief brief, int topK);
}
