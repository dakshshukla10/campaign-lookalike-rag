package io.github.dakshshukla.lookalike.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;
import io.github.dakshshukla.lookalike.retrieval.FeedbackWeightedRetriever;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

@IntegrationTest
class FeedbackWeightedRetrieverTest {

    private static final CampaignBrief BRIEF = new CampaignBrief("rice and lentils for pantry meals", null, null, null, null, null);

    private static Campaign campaign(String id, String description) {
        return new Campaign(id, "Pantry " + id, "grocery", "families", "video", "awareness", 1000, 7, 1, 0.01, 1.0, description);
    }

    @Autowired
    private CampaignRetriever plain;

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
                campaign("C-1", "rice and lentils for pantry meals"),
                campaign("C-2", "rice and lentils for weekly pantry meals at home"),
                campaign("C-3", "rice and beans for pantry cooking"),
                campaign("C-4", "flour and sugar for baking"),
                campaign("C-5", "dog leashes for walks")));
    }

    private List<String> ids(List<ScoredCampaign> result) {
        return result.stream().map(ScoredCampaign::id).toList();
    }

    @Test
    void withoutOutcomesTheOrderIsTheVectorSearchOrder() {
        var weighted = new FeedbackWeightedRetriever(plain, store, 0.25);

        assertThat(ids(weighted.retrieve(BRIEF, 3))).isEqualTo(ids(plain.retrieve(BRIEF, 3)));
    }

    @Test
    void unhelpfulReportsPushTheTopCampaignDown() {
        String top = ids(plain.retrieve(BRIEF, 5)).get(0);
        for (int i = 0; i < 8; i++) {
            store.record(top, false);
        }

        List<String> after = ids(new FeedbackWeightedRetriever(plain, store, 0.5).retrieve(BRIEF, 5));

        assertThat(after.get(0)).isNotEqualTo(top);
        assertThat(after).contains(top);
    }

    @Test
    void helpfulReportsLiftALowerCampaignToTheTop() {
        List<String> before = ids(plain.retrieve(BRIEF, 5));
        String runnerUp = before.get(2);
        for (int i = 0; i < 12; i++) {
            store.record(runnerUp, true);
            store.record(before.get(0), false);
        }

        List<String> after = ids(new FeedbackWeightedRetriever(plain, store, 0.5).retrieve(BRIEF, 5));

        assertThat(after.indexOf(runnerUp)).isLessThan(before.indexOf(runnerUp));
    }

    @Test
    void aCampaignJustOutsideTopKCanMoveIn() {
        List<String> before = ids(plain.retrieve(BRIEF, 5));
        String outside = before.get(2);
        for (int i = 0; i < 20; i++) {
            store.record(outside, true);
            store.record(before.get(0), false);
            store.record(before.get(1), false);
        }

        List<String> after = ids(new FeedbackWeightedRetriever(plain, store, 0.9).retrieve(BRIEF, 2));

        assertThat(after).hasSize(2).contains(outside);
    }

    @Test
    void campaignsWithoutReportsKeepTheirScore() {
        String reported = ids(plain.retrieve(BRIEF, 5)).get(0);
        store.record(reported, true);

        var weighted = new FeedbackWeightedRetriever(plain, store, 0.25).retrieve(BRIEF, 5);
        var raw = plain.retrieve(BRIEF, 5);

        for (ScoredCampaign candidate : weighted) {
            double rawScore = raw.stream().filter(r -> r.id().equals(candidate.id())).findFirst().orElseThrow().score();
            if (candidate.id().equals(reported)) {
                assertThat(candidate.score()).isGreaterThan(rawScore);
            } else {
                assertThat(candidate.score()).isEqualTo(rawScore);
            }
        }
    }

    @Test
    void neverReturnsMoreThanTopK() {
        for (int i = 0; i < 5; i++) {
            store.record("C-5", true);
        }

        assertThat(new FeedbackWeightedRetriever(plain, store, 0.25).retrieve(BRIEF, 2)).hasSize(2);
    }
}
