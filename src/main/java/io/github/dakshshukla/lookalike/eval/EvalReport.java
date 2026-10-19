package io.github.dakshshukla.lookalike.eval;

import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Result of one eval run. All rates are fractions between 0 and 1. The data behind them is
 * synthetic, so the numbers say whether the pipeline got better or worse, not how it would do
 * on real campaigns.
 */
public record EvalReport(Metrics metrics, List<BriefResult> briefs) {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * @param coverageAt1       final recommendations: rank 1 cites an expected campaign
     * @param coverageAt3       final recommendations: one of the top 3 cites an expected campaign
     * @param groundingRate     share of the ranker's first-attempt citations that were retrieved
     * @param finalGroundingRate the same share for the answer that was kept after retry and fallback
     * @param rerankCoverageAt1 coverage@1 if the reranked list were the answer
     * @param rerankCoverageAt3 coverage@3 if the reranked list were the answer
     * @param retrievalRecall   share of briefs with at least one expected campaign among the retrieved candidates
     * @param precisionAt3      average share of the top 3 recommendations that cite an expected campaign
     * @param fallbackRate      share of briefs where a slot was filled from reranked order
     */
    public record Metrics(
            int briefs,
            double coverageAt1,
            double coverageAt3,
            double groundingRate,
            double finalGroundingRate,
            double rerankCoverageAt1,
            double rerankCoverageAt3,
            double retrievalRecall,
            double precisionAt3,
            double fallbackRate) {
    }

    public record BriefResult(
            String id,
            boolean filtered,
            List<String> expected,
            List<String> recommended,
            boolean hitAt1,
            boolean hitAt3,
            boolean rerankHitAt1,
            boolean rerankHitAt3,
            boolean retrievalHit,
            double precisionAt3,
            boolean fallbackUsed,
            int attempts) {
    }

    static EvalReport of(List<BriefResult> results, double groundingRate, double finalGroundingRate) {
        int n = results.size();
        return new EvalReport(
                new Metrics(
                        n,
                        share(results, BriefResult::hitAt1),
                        share(results, BriefResult::hitAt3),
                        groundingRate,
                        finalGroundingRate,
                        share(results, BriefResult::rerankHitAt1),
                        share(results, BriefResult::rerankHitAt3),
                        share(results, BriefResult::retrievalHit),
                        results.stream().mapToDouble(BriefResult::precisionAt3).average().orElse(0.0),
                        share(results, BriefResult::fallbackUsed)),
                List.copyOf(results));
    }

    private static double share(List<BriefResult> results, java.util.function.Predicate<BriefResult> test) {
        return results.isEmpty() ? 0.0 : (double) results.stream().filter(test).count() / results.size();
    }

    public String toJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Short text for the console and the CI job summary. */
    public String toMarkdown() {
        Metrics m = metrics;
        StringBuilder out = new StringBuilder();
        out.append("| Metric | Value |\n| --- | --- |\n");
        row(out, "Briefs", String.valueOf(m.briefs()));
        row(out, "coverage@1", pct(m.coverageAt1()));
        row(out, "coverage@3", pct(m.coverageAt3()));
        row(out, "grounding rate (ranker, first attempt)", pct(m.groundingRate()));
        row(out, "grounding rate (final answer)", pct(m.finalGroundingRate()));
        row(out, "rerank-only coverage@1", pct(m.rerankCoverageAt1()));
        row(out, "rerank-only coverage@3", pct(m.rerankCoverageAt3()));
        row(out, "retrieval recall (any expected among candidates)", pct(m.retrievalRecall()));
        row(out, "precision@3 (not gated)", pct(m.precisionAt3()));
        row(out, "briefs that needed a fallback slot", pct(m.fallbackRate()));

        out.append("\nBy brief type:\n\n| Briefs | Count | coverage@1 | coverage@3 |\n| --- | --- | --- | --- |\n");
        for (boolean filtered : new boolean[] {false, true}) {
            List<BriefResult> group = briefs.stream().filter(b -> b.filtered() == filtered).toList();
            if (!group.isEmpty()) {
                out.append(String.format(Locale.ROOT, "| %s | %d | %s | %s |%n",
                        filtered ? "with filters" : "text only", group.size(),
                        pct(share(group, BriefResult::hitAt1)), pct(share(group, BriefResult::hitAt3))));
            }
        }
        List<String> missed = briefs.stream().filter(b -> !b.hitAt3()).map(BriefResult::id).toList();
        out.append("\nBriefs with no expected campaign in the top 3: ")
                .append(missed.isEmpty() ? "none" : String.join(", ", missed)).append('\n');
        return out.toString();
    }

    private static void row(StringBuilder out, String label, String value) {
        out.append("| ").append(label).append(" | ").append(value).append(" |\n");
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
