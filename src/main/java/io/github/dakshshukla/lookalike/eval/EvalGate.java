package io.github.dakshshukla.lookalike.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Compares an eval run with a committed baseline. A metric fails the gate when it falls more
 * than its allowed drop below the baseline value. Metrics are higher-is-better, so improving
 * never fails; refresh the baseline when a change is meant to move the numbers.
 *
 * <p>The baseline file holds {@code metrics} (the reference values) and {@code maxDrop} (how far
 * each may fall, as an absolute fraction). A metric without a {@code maxDrop} entry uses 0.
 */
public final class EvalGate {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** The metrics the gate watches, in report order. */
    static final List<String> WATCHED = List.of("coverageAt1", "coverageAt3", "groundingRate");

    public record Baseline(String note, Map<String, Double> metrics, Map<String, Double> maxDrop) {
    }

    public record Result(List<String> failures, List<String> lines) {
        public boolean passed() {
            return failures.isEmpty();
        }
    }

    private EvalGate() {
    }

    public static Baseline readBaseline(Path file) throws IOException {
        return JSON.readValue(file.toFile(), Baseline.class);
    }

    public static void writeBaseline(Path file, EvalReport report, Map<String, Double> maxDrop) throws IOException {
        Map<String, Double> metrics = currentValues(report.metrics());
        Baseline baseline = new Baseline(
                "Synthetic data only. Refresh with: ./gradlew eval -PevalUpdateBaseline=true", round(metrics), maxDrop);
        Files.writeString(file, JSON.writeValueAsString(baseline) + "\n");
    }

    public static Result compare(EvalReport.Metrics current, Baseline baseline) {
        Map<String, Double> now = currentValues(current);
        List<String> failures = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String name : WATCHED) {
            Double reference = baseline.metrics().get(name);
            if (reference == null) {
                failures.add(name + " has no baseline value");
                continue;
            }
            double allowed = baseline.maxDrop() == null ? 0.0 : baseline.maxDrop().getOrDefault(name, 0.0);
            double floor = reference - allowed;
            double value = now.get(name);
            // The small epsilon keeps a value equal to the floor from failing on float noise.
            boolean ok = value + 1e-9 >= floor;
            lines.add(String.format(Locale.ROOT, "%-14s %6.3f  baseline %6.3f  floor %6.3f  %s",
                    name, value, reference, floor, ok ? "ok" : "BELOW FLOOR"));
            if (!ok) {
                failures.add(String.format(Locale.ROOT, "%s fell to %.3f, below the floor of %.3f (baseline %.3f minus %.3f)",
                        name, value, floor, reference, allowed));
            }
        }
        return new Result(failures, lines);
    }

    static Map<String, Double> currentValues(EvalReport.Metrics m) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("coverageAt1", m.coverageAt1());
        values.put("coverageAt3", m.coverageAt3());
        values.put("groundingRate", m.groundingRate());
        return values;
    }

    private static Map<String, Double> round(Map<String, Double> metrics) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        metrics.forEach((k, v) -> rounded.put(k, Math.round(v * 10_000.0) / 10_000.0));
        return rounded;
    }
}
