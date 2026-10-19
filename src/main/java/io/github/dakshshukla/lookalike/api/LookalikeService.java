package io.github.dakshshukla.lookalike.api;

import java.util.List;

import org.springframework.stereotype.Service;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.grounding.GroundingResult;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.Reranker;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;

/**
 * Wires the pipeline: retrieve, rerank, LLM rank, then grounding validation. In Phase 1 the
 * retrieval, rerank and ranking steps are placeholders; the grounding validator is real.
 */
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
        List<Campaign> retrieved = retriever.retrieve(brief, CANDIDATES);
        List<Campaign> reranked = reranker.rerank(brief, retrieved);
        List<Recommendation> ranked = ranker.rank(brief, reranked, TOP_N);
        GroundingResult grounding = validator.validate(ranked, retrieved);

        return new LookalikeResponse(
                "stub",
                retrieved.size(),
                grounding.accepted(),
                new LookalikeResponse.Grounding(
                        grounding.citationsChecked(), grounding.citationsGrounded(), grounding.violations()),
                List.of("Phase 1 scaffold: retrieval, reranking and LLM ranking are not implemented yet."));
    }
}
