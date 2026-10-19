package io.github.dakshshukla.lookalike.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

class OutcomeWeightsTest {

    private static ScoredCampaign candidate(String id, double score) {
        return new ScoredCampaign(new Campaign(id, "n", "toys", "families", "video", "awareness", 1, 1, 1, 0.01, 1.0, ""), score);
    }

    @Test
    void noReportsMeansNoChange() {
        assertThat(new OutcomeStats(0, 0).signal()).isZero();
        assertThat(OutcomeWeights.multiplier(null, 0.25)).isEqualTo(1.0);
    }

    @Test
    void signalGrowsWithEvidenceAndStaysInsideMinusOneToOne() {
        assertThat(new OutcomeStats(1, 1).signal()).isCloseTo(1.0 / 3.0, offset(1e-9));
        assertThat(new OutcomeStats(10, 10).signal()).isGreaterThan(new OutcomeStats(1, 1).signal());
        assertThat(new OutcomeStats(0, 10).signal()).isLessThan(0).isGreaterThan(-1);
        assertThat(new OutcomeStats(5, 10).signal()).isZero();
    }

    @Test
    void multiplierIsBoundedByStrength() {
        assertThat(OutcomeWeights.multiplier(new OutcomeStats(1000, 1000), 0.25)).isBetween(1.2, 1.25);
        assertThat(OutcomeWeights.multiplier(new OutcomeStats(0, 1000), 0.25)).isBetween(0.75, 0.8);
    }

    @Test
    void statsRejectImpossibleCounts() {
        assertThatThrownBy(() -> new OutcomeStats(3, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutcomeStats(-1, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyReordersAndLeavesUnreportedCampaignsAlone() {
        var candidates = List.of(candidate("C-1", 0.50), candidate("C-2", 0.48), candidate("C-3", 0.30));
        var stats = Map.of("C-1", new OutcomeStats(0, 6), "C-2", new OutcomeStats(6, 6));

        var result = OutcomeWeights.apply(candidates, stats, 0.25);

        assertThat(result).extracting(ScoredCampaign::id).containsExactly("C-2", "C-1", "C-3");
        assertThat(result.get(2).score()).isEqualTo(0.30);
    }

    @Test
    void zeroStrengthKeepsTheOrder() {
        var candidates = List.of(candidate("C-1", 0.5), candidate("C-2", 0.4));
        var stats = Map.of("C-1", new OutcomeStats(0, 9), "C-2", new OutcomeStats(9, 9));

        assertThat(OutcomeWeights.apply(candidates, stats, 0.0)).isEqualTo(candidates);
    }

    @Test
    void equalScoresKeepTheIncomingOrder() {
        var candidates = List.of(candidate("C-1", 0.5), candidate("C-2", 0.5));

        assertThat(OutcomeWeights.apply(candidates, Map.of(), 0.25)).extracting(ScoredCampaign::id).containsExactly("C-1", "C-2");
    }
}
