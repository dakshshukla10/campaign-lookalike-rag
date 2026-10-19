package io.github.dakshshukla.lookalike.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalGateComparisonTest {

    private static final EvalGate.Baseline BASELINE = new EvalGate.Baseline(
            "test",
            Map.of("coverageAt1", 0.80, "coverageAt3", 0.90, "groundingRate", 1.0),
            Map.of("coverageAt1", 0.05, "coverageAt3", 0.05, "groundingRate", 0.0));

    private static EvalReport.Metrics metrics(double c1, double c3, double grounding) {
        return new EvalReport.Metrics(40, c1, c3, grounding, grounding, c1, c3, 1.0, 0.0, 0.0);
    }

    @Test
    void passesWhenEveryMetricIsAtOrAboveItsFloor() {
        EvalGate.Result result = EvalGate.compare(metrics(0.75, 0.90, 1.0), BASELINE);

        assertThat(result.passed()).isTrue();
        assertThat(result.lines()).hasSize(3);
    }

    @Test
    void improvingNeverFails() {
        assertThat(EvalGate.compare(metrics(0.99, 1.0, 1.0), BASELINE).passed()).isTrue();
    }

    @Test
    void failsWhenCoverageDropsPastTheAllowedAmount() {
        EvalGate.Result result = EvalGate.compare(metrics(0.70, 0.90, 1.0), BASELINE);

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).singleElement().asString().contains("coverageAt1").contains("0.700");
    }

    @Test
    void groundingHasNoSlack() {
        EvalGate.Result result = EvalGate.compare(metrics(0.80, 0.90, 0.99), BASELINE);

        assertThat(result.failures()).singleElement().asString().contains("groundingRate");
    }

    @Test
    void aMetricMissingFromTheBaselineFails() {
        var partial = new EvalGate.Baseline("test", Map.of("coverageAt1", 0.8), Map.of());

        assertThat(EvalGate.compare(metrics(0.8, 0.9, 1.0), partial).failures()).hasSize(2);
    }

    @Test
    void baselineRoundTripsThroughTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("baseline.json");
        EvalReport report = new EvalReport(metrics(0.8333333, 0.9, 1.0), java.util.List.of());

        EvalGate.writeBaseline(file, report, Map.of("coverageAt1", 0.02));
        EvalGate.Baseline read = EvalGate.readBaseline(file);

        assertThat(read.metrics()).containsEntry("coverageAt1", 0.8333).containsEntry("groundingRate", 1.0);
        assertThat(read.maxDrop()).containsEntry("coverageAt1", 0.02);
    }
}
