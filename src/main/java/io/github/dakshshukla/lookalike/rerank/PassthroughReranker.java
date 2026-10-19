package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/** Phase 1 placeholder: keeps the retrieval order unchanged. */
@Component
public class PassthroughReranker implements Reranker {

    @Override
    public List<Campaign> rerank(CampaignBrief brief, List<Campaign> candidates) {
        return candidates;
    }
}
