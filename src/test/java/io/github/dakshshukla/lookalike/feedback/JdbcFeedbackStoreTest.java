package io.github.dakshshukla.lookalike.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

@IntegrationTest
class JdbcFeedbackStoreTest {

    @Autowired
    private FeedbackStore store;

    @Autowired
    private CampaignIngestService ingest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE campaigns, vector_store CASCADE");
        ingest.ingest(List.of(
                new Campaign("C-1", "One", "toys", "families", "video", "awareness", 1, 1, 1, 0.01, 1.0, "bricks"),
                new Campaign("C-2", "Two", "pet", "seniors", "email", "retention", 1, 1, 1, 0.01, 1.0, "leashes"),
                new Campaign("C-3", "Three", "home", "students", "social", "conversion", 1, 1, 1, 0.01, 1.0, "lamps")));
    }

    @Test
    void countsHelpfulAndTotalReportsPerCampaign() {
        store.record("C-1", true);
        store.record("C-1", true);
        store.record("C-1", false);
        store.record("C-2", false);

        var stats = store.statsFor(List.of("C-1", "C-2", "C-3"));

        assertThat(stats.get("C-1")).isEqualTo(new OutcomeStats(2, 3));
        assertThat(stats.get("C-2")).isEqualTo(new OutcomeStats(0, 1));
        assertThat(stats).doesNotContainKey("C-3");
    }

    @Test
    void statsOnlyCoverTheRequestedCampaigns() {
        store.record("C-1", true);
        store.record("C-2", true);

        assertThat(store.statsFor(List.of("C-2"))).containsOnlyKeys("C-2");
        assertThat(store.statsFor(List.of())).isEmpty();
    }

    @Test
    void anUnknownCampaignIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> store.record("C-404", true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void clearRemovesEveryOutcome() {
        store.record("C-1", true);

        store.clear();

        assertThat(store.statsFor(List.of("C-1"))).isEmpty();
    }

    @Test
    void outcomesGoWhenTheirCampaignIsDeleted() {
        store.record("C-1", true);

        jdbc.update("DELETE FROM campaigns WHERE campaign_id = 'C-1'");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM outcomes", Integer.class)).isZero();
    }
}
