package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;

/** Phase 1 placeholder: no LLM is called and no recommendations are produced. */
@Component
public class NoopRecommendationRanker implements RecommendationRanker {

    @Override
    public List<Recommendation> rank(CampaignBrief brief, List<Campaign> rerankedCandidates, int limit) {
        return List.of();
    }
}
