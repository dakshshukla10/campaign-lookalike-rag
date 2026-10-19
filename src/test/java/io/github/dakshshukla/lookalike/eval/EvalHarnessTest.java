package io.github.dakshshukla.lookalike.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.dakshshukla.lookalike.api.PipelineResult;
import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingResult;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;

class EvalHarnessTest {

    private static ScoredCampaign scored(String id) {
        return new ScoredCampaign(
                new Campaign(id, "n", "toys", "families", "video", "awareness", 1, 1, 1, 0.01, 1.0, ""), 0.5);
    }

    private static Recommendation rec(int rank, String id) {
        return new Recommendation(rank, "s", List.of(new Citation(id, "r")));
    }

    private static LabelledBrief labelled(String id, boolean filtered, String... expected) {
        return new LabelledBrief(id, "text", filtered ? "toys" : null, null, null, null, null, List.of(expected));
    }

    /** Builds a pipeline result from the ids retrieved, the ids the ranker cited and its citation counts. */
    private static PipelineResult result(List<String> retrieved, List<String> cited, boolean fallback, int checked, int grounded) {
        List<ScoredCampaign> candidates = retrieved.stream().map(EvalHarnessTest::scored).toList();
        List<Recommendation> recs = new java.util.ArrayList<>();
        for (int i = 0; i < cited.size(); i++) {
            recs.add(rec(i + 1, cited.get(i)));
        }
        GroundingResult first = new GroundingResult(recs, List.of(), checked, grounded);
        GroundingResult empty = new GroundingValidator().validate(List.of(), List.of());
        return new PipelineResult(new CampaignBrief("t", null, null, null, null, null), candidates, candidates, recs,
                first, checked == 0 ? empty : first, 1, fallback, List.of());
    }

    @Test
    void computesCoverageAtOneAndThreeAsHitRates() {
        var briefs = List.of(
                labelled("B-1", false, "C-1"),
                labelled("B-2", false, "C-2"),
                labelled("B-3", true, "C-3"),
                labelled("B-4", true, "C-4"));
        var runs = Map.of(
                "B-1", result(List.of("C-1", "C-9"), List.of("C-1", "C-9", "C-8"), false, 3, 3),
                "B-2", result(List.of("C-9", "C-2"), List.of("C-9", "C-2", "C-8"), false, 3, 3),
                "B-3", result(List.of("C-9", "C-8", "C-7", "C-3"), List.of("C-9", "C-8", "C-7"), false, 3, 3),
                "B-4", result(List.of("C-9"), List.of("C-9"), true, 1, 1));
        var next = new java.util.concurrent.atomic.AtomicInteger();
        var harness = new EvalHarness(brief -> runs.get("B-" + next.incrementAndGet()));

        EvalReport report = harness.run(briefs);

        assertThat(report.metrics().coverageAt1()).isEqualTo(0.25);
        assertThat(report.metrics().coverageAt3()).isEqualTo(0.5);
        assertThat(report.metrics().retrievalRecall()).isEqualTo(0.75);
        assertThat(report.metrics().rerankCoverageAt1()).isEqualTo(0.25);
        assertThat(report.metrics().fallbackRate()).isEqualTo(0.25);
        assertThat(report.briefs()).extracting(EvalReport.BriefResult::hitAt3).containsExactly(true, true, false, false);
    }

    @Test
    void precisionAtThreeAveragesOverBriefs() {
        var harness = new EvalHarness(brief -> result(List.of("C-1", "C-2", "C-3"), List.of("C-1", "C-9", "C-2"), false, 3, 3));

        EvalReport report = harness.run(List.of(labelled("B-1", false, "C-1", "C-2")));

        assertThat(report.metrics().precisionAt3()).isEqualTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void groundingRateIsCitationsGroundedOverChecked() {
        var harness = new EvalHarness(brief -> result(List.of("C-1"), List.of("C-1"), false, 4, 3));

        EvalReport report = harness.run(List.of(labelled("B-1", false, "C-1"), labelled("B-2", false, "C-1")));

        assertThat(report.metrics().groundingRate()).isEqualTo(0.75);
    }

    @Test
    void aBriefWithNoRecommendationsCountsAsAMiss() {
        var harness = new EvalHarness(brief -> result(List.of(), List.of(), false, 0, 0));

        EvalReport report = harness.run(List.of(labelled("B-1", false, "C-1")));

        assertThat(report.metrics().coverageAt1()).isZero();
        assertThat(report.metrics().groundingRate()).isEqualTo(1.0);
        assertThat(report.briefs().get(0).recommended()).isEmpty();
    }

    @Test
    void reportRendersMarkdownAndJson() {
        var harness = new EvalHarness(brief -> result(List.of("C-1"), List.of("C-1"), false, 1, 1));

        EvalReport report = harness.run(List.of(labelled("B-1", false, "C-1")));

        assertThat(report.toMarkdown()).contains("coverage@1 | 100.0%").contains("none");
        assertThat(report.toJson()).contains("\"coverageAt1\" : 1.0");
    }

    @Test
    void loaderReadsTheCommittedBriefs() throws IOException {
        List<LabelledBrief> briefs = LabelledBriefLoader.load(Path.of("data", "eval_briefs.json"));

        assertThat(briefs).hasSize(40);
        assertThat(briefs).allSatisfy(b -> assertThat(b.expected()).isNotEmpty());
        assertThat(briefs.stream().filter(LabelledBrief::hasFilters).count()).isEqualTo(20);
    }

    @Test
    void loaderRejectsABriefWithoutLabels(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("briefs.json");
        Files.writeString(file, "{\"briefs\":[{\"id\":\"B-1\",\"description\":\"x\",\"expected\":[]}]}");

        assertThatThrownBy(() -> LabelledBriefLoader.load(file)).isInstanceOf(IOException.class).hasMessageContaining("B-1");
    }
}
