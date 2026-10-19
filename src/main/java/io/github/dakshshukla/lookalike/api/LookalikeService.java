package io.github.dakshshukla.lookalike.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingResult;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.grounding.Violation;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.Reranker;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;

/**
 * Wires the pipeline: retrieve, rerank, rank, validate. When the validator rejects part of the
 * ranker's answer the ranker is asked again with the rejected ids named, up to
 * {@code lookalike.ranking.max-attempts} times. If slots are still missing after that, they are
 * filled from reranked order and marked as {@code FALLBACK}, so the response never carries a
 * citation that was not retrieved and never silently hides that the ranker failed.
 */
@Service
public class LookalikeService {

    private static final Logger log = LoggerFactory.getLogger(LookalikeService.class);

    static final int CANDIDATES = 20;
    static final int TOP_N = 3;

    private final CampaignRetriever retriever;
    private final Reranker reranker;
    private final RecommendationRanker ranker;
    private final GroundingValidator validator;
    private final int maxAttempts;

    public LookalikeService(
            CampaignRetriever retriever,
            Reranker reranker,
            RecommendationRanker ranker,
            GroundingValidator validator,
            @Value("${lookalike.ranking.max-attempts:2}") int maxAttempts) {
        this.retriever = retriever;
        this.reranker = reranker;
        this.ranker = ranker;
        this.validator = validator;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    public LookalikeResponse lookalikes(CampaignBrief brief) {
        PipelineResult result = run(brief);
        GroundingResult grounding = result.finalGrounding();
        return new LookalikeResponse(
                result.status(),
                result.retrieved().size(),
                result.recommendations(),
                new LookalikeResponse.Grounding(
                        grounding.citationsChecked(), grounding.citationsGrounded(), grounding.violations()),
                result.notes());
    }

    public PipelineResult run(CampaignBrief brief) {
        List<ScoredCampaign> retrieved = retriever.retrieve(brief, CANDIDATES);
        List<Campaign> retrievedCampaigns = retrieved.stream().map(ScoredCampaign::campaign).toList();
        GroundingResult empty = validator.validate(List.of(), retrievedCampaigns);
        if (retrieved.isEmpty()) {
            return new PipelineResult(brief, retrieved, retrieved, List.of(), empty, empty, 0, false,
                    List.of("No campaign matched the brief and its filters."));
        }

        List<ScoredCampaign> reranked = reranker.rerank(brief, retrieved);
        List<String> notes = new ArrayList<>();

        GroundingResult first = attempt(brief, reranked, retrievedCampaigns, null, notes);
        GroundingResult kept = first;
        int attempts = 1;
        while (needsAnotherAttempt(kept) && attempts < maxAttempts) {
            notes.add("Asked the ranker again after " + describe(kept) + ".");
            GroundingResult next = attempt(brief, reranked, retrievedCampaigns, kept.violations(), notes);
            attempts++;
            kept = better(next, kept);
        }

        List<Recommendation> finalRecs = new ArrayList<>(kept.accepted());
        boolean fallback = false;
        int wanted = Math.min(TOP_N, reranked.size());
        if (needsAnotherAttempt(kept) && finalRecs.size() < wanted) {
            fallback = true;
            fillFromRetrievalOrder(finalRecs, reranked, wanted);
            notes.add("Filled " + (wanted - kept.accepted().size())
                    + " slot(s) from reranked order because the ranker's output was rejected or missing.");
        }
        List<Recommendation> numbered = new ArrayList<>();
        for (int i = 0; i < finalRecs.size(); i++) {
            numbered.add(finalRecs.get(i).withRank(i + 1));
        }
        return new PipelineResult(brief, retrieved, reranked, numbered, first, kept, attempts, fallback, List.copyOf(notes));
    }

    private GroundingResult attempt(
            CampaignBrief brief,
            List<ScoredCampaign> reranked,
            List<Campaign> retrieved,
            List<Violation> previous,
            List<String> notes) {
        List<Recommendation> ranked;
        try {
            ranked = previous == null
                    ? ranker.rank(brief, reranked, TOP_N)
                    : ranker.retry(brief, reranked, TOP_N, previous);
        } catch (RuntimeException e) {
            // A provider outage or unparseable JSON ends up here; treat it like an empty answer.
            log.warn("Ranker call failed: {}", e.toString());
            notes.add("The ranker call failed: " + e.getClass().getSimpleName() + ".");
            ranked = List.of();
        }
        return validator.validate(ranked, retrieved);
    }

    /** Something was rejected, or the ranker returned nothing at all. */
    private static boolean needsAnotherAttempt(GroundingResult result) {
        return !result.violations().isEmpty() || result.accepted().isEmpty();
    }

    private static GroundingResult better(GroundingResult candidate, GroundingResult current) {
        if (candidate.accepted().size() != current.accepted().size()) {
            return candidate.accepted().size() > current.accepted().size() ? candidate : current;
        }
        return candidate.violations().size() < current.violations().size() ? candidate : current;
    }

    private static String describe(GroundingResult result) {
        if (result.violations().isEmpty()) {
            return "an empty answer";
        }
        return result.violations().size() + " rejected citation(s) ("
                + result.violations().stream()
                        .map(v -> v.citedId() + ": " + v.reason())
                        .distinct()
                        .limit(3)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("")
                + ")";
    }

    private static void fillFromRetrievalOrder(List<Recommendation> recs, List<ScoredCampaign> reranked, int wanted) {
        Set<String> alreadyCited = new HashSet<>();
        recs.forEach(r -> r.citations().forEach(c -> alreadyCited.add(c.campaignId())));
        for (ScoredCampaign candidate : reranked) {
            if (recs.size() >= wanted) {
                return;
            }
            if (alreadyCited.add(candidate.id())) {
                Campaign c = candidate.campaign();
                recs.add(new Recommendation(
                        recs.size() + 1,
                        c.name() + " (" + c.category() + ", " + c.channel() + ", " + c.objective()
                                + ") is among the closest matches by reranked score.",
                        List.of(new Citation(c.id(), "Next best reranked candidate, score "
                                + String.format(java.util.Locale.ROOT, "%.3f", candidate.score()))),
                        Recommendation.Source.FALLBACK));
            }
        }
    }
}
