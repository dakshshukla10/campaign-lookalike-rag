package io.github.dakshshukla.lookalike.api;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingResult;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.Reranker;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;

/** Wires the pipeline: retrieve, rerank, rank, then grounding validation. */
@Service
public class LookalikeService {

    static final int CANDIDATES = 20;
    static final int TOP_N = 3;

    private final CampaignRetriever retriever;
    private final Reranker reranker;
    private final RecommendationRanker ranker;
    private final GroundingValidator validator;

    public LookalikeService(
            CampaignRetriever retriever,
            Reranker reranker,
            RecommendationRanker ranker,
            GroundingValidator validator) {
        this.retriever = retriever;
        this.reranker = reranker;
        this.ranker = ranker;
        this.validator = validator;
    }

    public LookalikeResponse lookalikes(CampaignBrief brief) {
        List<ScoredCampaign> retrieved = retriever.retrieve(brief, CANDIDATES);
        List<ScoredCampaign> reranked = reranker.rerank(brief, retrieved);
        List<Recommendation> ranked = ranker.rank(brief, reranked, TOP_N);
        List<Campaign> retrievedCampaigns = retrieved.stream().map(ScoredCampaign::campaign).toList();
        GroundingResult grounding = validator.validate(ranked, retrievedCampaigns);

        return new LookalikeResponse(
                retrieved.isEmpty() ? "no_matches" : "ok",
                retrieved.size(),
                grounding.accepted(),
                new LookalikeResponse.Grounding(
                        grounding.citationsChecked(), grounding.citationsGrounded(), grounding.violations()),
                List.of());
    }
}
