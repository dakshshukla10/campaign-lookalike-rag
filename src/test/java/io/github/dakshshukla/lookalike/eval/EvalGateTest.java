package io.github.dakshshukla.lookalike.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.api.LookalikeService;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

/**
 * The CI regression gate. Loads the synthetic campaigns, runs the labelled briefs through the
 * whole pipeline with the default offline models, prints the metrics and fails when one falls
 * below {@code eval/baseline.json}. Run it with {@code ./gradlew eval}; add
 * {@code -PevalUpdateBaseline=true} to write a new baseline instead of checking.
 */
@IntegrationTest
@Tag("eval")
class EvalGateTest {

    private static final Path CAMPAIGNS = Path.of("data", "sample_campaigns.csv");
    private static final Path BRIEFS = Path.of("data", "eval_briefs.json");
    private static final Path BASELINE = Path.of("eval", "baseline.json");
    private static final Path REPORT_DIR = Path.of("build", "reports", "eval");

    /** How far each metric may fall below the baseline before the build fails. */
    private static final Map<String, Double> MAX_DROP = Map.of(
            "coverageAt1", 0.025,
            "coverageAt3", 0.025,
            "groundingRate", 0.0);

    @Autowired
    private LookalikeService service;

    @Autowired
    private CampaignIngestService ingest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void metricsStayAtOrAboveTheBaseline() throws Exception {
        jdbc.execute("TRUNCATE campaigns, vector_store CASCADE");
        ingest.ingest(CAMPAIGNS);

        List<LabelledBrief> briefs = LabelledBriefLoader.load(BRIEFS);
        EvalReport report = new EvalHarness(service::run).run(briefs);

        Files.createDirectories(REPORT_DIR);
        Files.writeString(REPORT_DIR.resolve("eval-report.json"), report.toJson());
        Files.writeString(REPORT_DIR.resolve("eval-report.md"), report.toMarkdown());
        System.out.println("\nEval on synthetic data (" + briefs.size() + " briefs)\n" + report.toMarkdown());

        if (Boolean.parseBoolean(System.getProperty("eval.updateBaseline"))) {
            EvalGate.writeBaseline(BASELINE, report, MAX_DROP);
            System.out.println("Wrote " + BASELINE);
            return;
        }

        EvalGate.Result result = EvalGate.compare(report.metrics(), EvalGate.readBaseline(BASELINE));
        result.lines().forEach(System.out::println);
        assertThat(result.failures()).as("metrics below the committed baseline").isEmpty();
    }
}
