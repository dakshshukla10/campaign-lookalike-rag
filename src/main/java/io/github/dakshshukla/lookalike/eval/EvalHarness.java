package io.github.dakshshukla.lookalike.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import io.github.dakshshukla.lookalike.api.PipelineResult;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/**
 * Runs labelled briefs through the pipeline and reports how often an expected campaign shows up.
 *
 * <p>Coverage is a hit rate: a brief is covered at K when at least one of the first K results
 * cites an expected campaign. It is measured on the final recommendations, the thing a caller
 * sees, with the rerank and retrieval stages reported next to it to show where a miss started.
 * The grounding rate uses the ranker's first answer, before any retry or fallback, so it shows
 * what the ranker did on its own.
 */
public class EvalHarness {

    private final Function<CampaignBrief, PipelineResult> pipeline;

    public EvalHarness(Function<CampaignBrief, PipelineResult> pipeline) {
        this.pipeline = pipeline;
    }

    public EvalReport run(List<LabelledBrief> briefs) {
        List<EvalReport.BriefResult> results = new ArrayList<>();
        int checked = 0;
        int grounded = 0;
        int finalChecked = 0;
        int finalGrounded = 0;

        for (LabelledBrief labelled : briefs) {
            PipelineResult run = pipeline.apply(labelled.toBrief());
            Set<String> expected = Set.copyOf(labelled.expected());

            List<String> recommended = run.recommendations().stream()
                    .map(r -> r.citations().isEmpty() ? "" : r.citations().get(0).campaignId())
                    .toList();
            List<String> reranked = run.reranked().stream().map(ScoredCampaign::id).toList();
            List<String> retrieved = run.retrieved().stream().map(ScoredCampaign::id).toList();

            results.add(new EvalReport.BriefResult(
                    labelled.id(),
                    labelled.hasFilters(),
                    labelled.expected(),
                    recommended,
                    hitWithin(run.recommendations(), expected, 1),
                    hitWithin(run.recommendations(), expected, 3),
                    anyWithin(reranked, expected, 1),
                    anyWithin(reranked, expected, 3),
                    anyWithin(retrieved, expected, retrieved.size()),
                    precision(run.recommendations(), expected, 3),
                    run.fallbackUsed(),
                    run.attempts()));

            checked += run.firstAttempt().citationsChecked();
            grounded += run.firstAttempt().citationsGrounded();
            finalChecked += run.finalGrounding().citationsChecked();
            finalGrounded += run.finalGrounding().citationsGrounded();
        }
        return EvalReport.of(results, rate(grounded, checked), rate(finalGrounded, finalChecked));
    }

    private static boolean hitWithin(List<Recommendation> recommendations, Set<String> expected, int k) {
        return recommendations.stream()
                .limit(k)
                .anyMatch(r -> r.citations().stream().anyMatch(c -> expected.contains(c.campaignId())));
    }

    private static double precision(List<Recommendation> recommendations, Set<String> expected, int k) {
        List<Recommendation> top = recommendations.stream().limit(k).toList();
        if (top.isEmpty()) {
            return 0.0;
        }
        long hits = top.stream()
                .filter(r -> r.citations().stream().anyMatch(c -> expected.contains(c.campaignId())))
                .count();
        return (double) hits / top.size();
    }

    private static boolean anyWithin(List<String> ids, Set<String> expected, int k) {
        return ids.stream().limit(k).anyMatch(expected::contains);
    }

    /** With nothing to check there is nothing ungrounded, so the rate is 1. */
    private static double rate(int part, int whole) {
        return whole == 0 ? 1.0 : (double) part / whole;
    }
}
