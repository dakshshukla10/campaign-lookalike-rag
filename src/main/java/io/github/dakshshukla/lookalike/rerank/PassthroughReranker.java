package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/** Keeps the retrieval order. Selected with {@code lookalike.rerank.mode=none}. */
public class PassthroughReranker implements Reranker {

    @Override
    public List<ScoredCampaign> rerank(CampaignBrief brief, List<ScoredCampaign> candidates) {
        return candidates;
    }
}
