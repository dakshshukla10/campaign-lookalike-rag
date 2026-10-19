package io.github.dakshshukla.lookalike.api;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingResult;

/**
 * Everything one pipeline run produced. The HTTP response is a trimmed view of this; the eval
 * harness reads the rest, in particular {@code firstAttempt}, which shows what the ranker did
 * before any retry or fallback.
 *
 * @param retrieved       candidates from the vector search, best first
 * @param reranked        the same candidates after reranking
 * @param recommendations final, grounded recommendations
 * @param firstAttempt    grounding result of the ranker's first answer
 * @param finalGrounding  grounding result of the answer that was kept
 * @param attempts        how many times the ranker was asked
 * @param fallbackUsed    true when a slot was filled from retrieval order
 */
public record PipelineResult(
        CampaignBrief brief,
        List<ScoredCampaign> retrieved,
        List<ScoredCampaign> reranked,
        List<Recommendation> recommendations,
        GroundingResult firstAttempt,
        GroundingResult finalGrounding,
        int attempts,
        boolean fallbackUsed,
        List<String> notes) {

    public String status() {
        if (retrieved.isEmpty()) {
            return "no_matches";
        }
        return fallbackUsed ? "degraded" : "ok";
    }
}
