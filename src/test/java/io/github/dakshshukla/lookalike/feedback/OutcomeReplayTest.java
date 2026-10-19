package io.github.dakshshukla.lookalike.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.api.LookalikeService;
import io.github.dakshshukla.lookalike.api.PipelineResult;
import io.github.dakshshukla.lookalike.eval.EvalHarness;
import io.github.dakshshukla.lookalike.eval.EvalReport;
import io.github.dakshshukla.lookalike.eval.LabelledBrief;
import io.github.dakshshukla.lookalike.eval.LabelledBriefLoader;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;
import io.github.dakshshukla.lookalike.rerank.Reranker;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;
import io.github.dakshshukla.lookalike.retrieval.FeedbackWeightedRetriever;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

/**
 * Offline replay. Outcomes are recorded by running half of the labelled briefs and marking each
 * recommendation helpful when it was an expected campaign. The other half is then evaluated with
 * and without re-weighting. A second replay feeds the same outcomes with the verdicts flipped to
 * show that the weighting really does move rankings, and in the direction the outcomes say.
 */
@IntegrationTest
class OutcomeReplayTest {

    @Autowired
    private CampaignRetriever vectorSearch;

    @Autowired
    private Reranker reranker;

    @Autowired
    private RecommendationRanker ranker;

    @Autowired
    private GroundingValidator validator;

    @Autowired
    private FeedbackStore store;

    @Autowired
    private CampaignIngestService ingest;

    @Autowired
    private JdbcTemplate jdbc;

    private List<LabelledBrief> training;
    private List<LabelledBrief> holdout;

    @BeforeEach
    void loadData() throws Exception {
        jdbc.execute("TRUNCATE campaigns, vector_store CASCADE");
        ingest.ingest(Path.of("data", "sample_campaigns.csv"));
        store.clear();

        List<LabelledBrief> all = LabelledBriefLoader.load(Path.of("data", "eval_briefs.json"));
        training = new ArrayList<>();
        holdout = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            (i % 2 == 0 ? training : holdout).add(all.get(i));
        }
    }

    private LookalikeService service(CampaignRetriever retriever) {
        return new LookalikeService(retriever, reranker, ranker, validator, 2);
    }

    /** Runs the training briefs and records an outcome for every recommendation. */
    private int recordOutcomes(LookalikeService plain, boolean flipVerdicts) {
        int recorded = 0;
        for (LabelledBrief labelled : training) {
            Set<String> expected = Set.copyOf(labelled.expected());
            PipelineResult run = plain.run(labelled.toBrief());
            for (var rec : run.recommendations()) {
                String id = rec.citations().get(0).campaignId();
                store.record(id, expected.contains(id) != flipVerdicts);
                recorded++;
            }
        }
        return recorded;
    }

    private static int briefsWithDifferentRanking(EvalReport a, EvalReport b) {
        int changed = 0;
        for (int i = 0; i < a.briefs().size(); i++) {
            if (!a.briefs().get(i).recommended().equals(b.briefs().get(i).recommended())) {
                changed++;
            }
        }
        return changed;
    }

    @Test
    void replayingTrueOutcomesAtTheDefaultStrengthDoesNotHurtHeldOutBriefs() {
        LookalikeService plain = service(vectorSearch);
        EvalReport before = new EvalHarness(plain::run).run(holdout);

        int recorded = recordOutcomes(plain, false);
        LookalikeService weighted = service(
                new FeedbackWeightedRetriever(vectorSearch, store, OutcomeWeights.DEFAULT_STRENGTH));
        EvalReport after = new EvalHarness(weighted::run).run(holdout);

        System.out.printf("Replay on %d held-out briefs, %d outcomes recorded: coverage@1 %.3f -> %.3f, "
                        + "coverage@3 %.3f -> %.3f, grounding %.3f -> %.3f, %d rankings changed%n",
                holdout.size(), recorded,
                before.metrics().coverageAt1(), after.metrics().coverageAt1(),
                before.metrics().coverageAt3(), after.metrics().coverageAt3(),
                before.metrics().groundingRate(), after.metrics().groundingRate(),
                briefsWithDifferentRanking(before, after));

        assertThat(recorded).isPositive();
        assertThat(after.metrics().coverageAt1()).isGreaterThanOrEqualTo(before.metrics().coverageAt1());
        assertThat(after.metrics().coverageAt3()).isGreaterThanOrEqualTo(before.metrics().coverageAt3());
        assertThat(after.metrics().groundingRate()).isEqualTo(1.0);
        assertThat(after.metrics().finalGroundingRate()).isEqualTo(1.0);
    }

    @Test
    void trueOutcomesRaisePrecisionAndFlippedOnesCannotRaiseIt() {
        // Replays on the same briefs the outcomes came from, with a larger weight, to isolate the mechanism.
        double strength = 0.5;
        LookalikeService plain = service(vectorSearch);
        EvalReport before = new EvalHarness(plain::run).run(training);

        recordOutcomes(plain, false);
        EvalReport lifted = new EvalHarness(service(new FeedbackWeightedRetriever(vectorSearch, store, strength))::run).run(training);

        store.clear();
        recordOutcomes(plain, true);
        EvalReport pushedDown = new EvalHarness(service(new FeedbackWeightedRetriever(vectorSearch, store, strength))::run).run(training);

        System.out.printf("Replay on the %d briefs the outcomes came from, strength %.1f: precision@3 %.3f -> %.3f with true outcomes, "
                        + "-> %.3f with flipped outcomes, %d rankings changed%n",
                training.size(), strength, before.metrics().precisionAt3(), lifted.metrics().precisionAt3(),
                pushedDown.metrics().precisionAt3(), briefsWithDifferentRanking(before, pushedDown));

        assertThat(lifted.metrics().precisionAt3()).isGreaterThan(before.metrics().precisionAt3());
        assertThat(pushedDown.metrics().precisionAt3()).isLessThanOrEqualTo(before.metrics().precisionAt3());
        assertThat(briefsWithDifferentRanking(before, pushedDown)).isPositive();
        // Whatever the weights do, citations stay inside the retrieved set.
        assertThat(lifted.metrics().groundingRate()).isEqualTo(1.0);
        assertThat(pushedDown.metrics().groundingRate()).isEqualTo(1.0);
    }
}
