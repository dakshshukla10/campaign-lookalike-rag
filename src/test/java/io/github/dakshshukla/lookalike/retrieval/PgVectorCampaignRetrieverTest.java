package io.github.dakshshukla.lookalike.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

@IntegrationTest
class PgVectorCampaignRetrieverTest {

    private static Campaign campaign(String id, String name, String category, String audience, String channel,
            String objective, String description) {
        return new Campaign(id, name, category, audience, channel, objective, 50_000, 14, 1_000_000, 0.012, 2.5, description);
    }

    private static final List<Campaign> SEED = List.of(
            campaign("C-0001", "Winter pantry staples awareness push", "grocery", "families", "video", "awareness",
                    "Fill the pantry with rice and lentils for meals through the week."),
            campaign("C-0002", "Spring pantry staples conversion push", "grocery", "students", "email", "conversion",
                    "Stock up on rice, lentils and canned beans for affordable meals."),
            campaign("C-0003", "Holiday baking retention push", "grocery", "families", "email", "retention",
                    "Flour, sugar and butter for family baking over the holidays."),
            campaign("C-0004", "Summer skincare launch awareness push", "beauty", "young-adults", "social", "awareness",
                    "A new sunscreen and light moisturiser for hot days outside."),
            campaign("C-0005", "Winter skincare launch consideration push", "beauty", "seniors", "video", "consideration",
                    "A rich night cream and gentle cleanser for dry winter skin."),
            campaign("C-0006", "Weekend dog walks conversion push", "pet", "families", "social", "conversion",
                    "Leashes, harnesses and treats for daily dog walks."),
            campaign("C-0007", "Spring cat feeding retention push", "pet", "seniors", "display", "retention",
                    "Kibble and wet food for a steady feline feeding routine."),
            campaign("C-0008", "Back-to-School desk lamps awareness push", "home", "students", "display", "awareness",
                    "Bright desk lamps for evening study at home."),
            campaign("C-0009", "Holiday smart plugs consideration push", "electronics", "families", "sponsored-search", "consideration",
                    "Smart plugs that let households control a lamp from another room."),
            campaign("C-0010", "Winter cardigan layering conversion push", "apparel", "young-adults", "video", "conversion",
                    "Layer a cardigan over a simple top for cold commutes."),
            campaign("C-0011", "Summer picnic baskets awareness push", "home", "families", "in-store-screens", "awareness",
                    "Picnic baskets and blankets for weekend outings."),
            campaign("C-0012", "Weekend vitamin bundles retention push", "health", "seniors", "email", "retention",
                    "Daily vitamin bundles delivered every month."));

    @Autowired
    private CampaignRetriever retriever;

    @Autowired
    private CampaignIngestService ingest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE campaigns, vector_store CASCADE");
        ingest.ingest(SEED);
    }

    private static CampaignBrief brief(String description) {
        return new CampaignBrief(description, null, null, null, null, null);
    }

    @Test
    void withoutFiltersReturnsTopKBestFirst() {
        List<ScoredCampaign> result = retriever.retrieve(brief("rice and lentils for pantry meals"), 5);

        assertThat(result).hasSize(5);
        assertThat(result).extracting(ScoredCampaign::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void aBriefWordedLikeACampaignFindsItFirst() {
        List<ScoredCampaign> result = retriever.retrieve(brief("smart plugs to control a lamp from another room"), 3);

        assertThat(result.get(0).id()).isEqualTo("C-0009");
    }

    @Test
    void topKLimitsTheResultCount() {
        assertThat(retriever.retrieve(brief("campaign"), 2)).hasSize(2);
        assertThat(retriever.retrieve(brief("campaign"), 50)).hasSize(SEED.size());
    }

    @Test
    void resultsCarryTheFullCampaignRow() {
        ScoredCampaign top = retriever.retrieve(brief("smart plugs to control a lamp"), 1).get(0);

        assertThat(top.campaign()).isEqualTo(SEED.get(8));
    }

    @Test
    void categoryFilterOnlyReturnsThatCategory() {
        var result = retriever.retrieve(new CampaignBrief("campaign", "grocery", null, null, null, null), 20);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-0001", "C-0002", "C-0003");
    }

    @Test
    void audienceFilterOnlyReturnsThatAudience() {
        var result = retriever.retrieve(new CampaignBrief("campaign", null, "seniors", null, null, null), 20);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-0005", "C-0007", "C-0012");
    }

    @Test
    void channelFilterOnlyReturnsThatChannel() {
        var result = retriever.retrieve(new CampaignBrief("campaign", null, null, "in-store-screens", null, null), 20);

        assertThat(result).extracting(ScoredCampaign::id).containsExactly("C-0011");
    }

    @Test
    void objectiveFilterOnlyReturnsThatObjective() {
        var result = retriever.retrieve(new CampaignBrief("campaign", null, null, null, "retention", null), 20);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-0003", "C-0007", "C-0012");
    }

    @Test
    void filtersCombineWithAnd() {
        var two = retriever.retrieve(new CampaignBrief("campaign", "grocery", null, "email", null, null), 20);
        var four = retriever.retrieve(new CampaignBrief("campaign", "grocery", "students", "email", "conversion", null), 20);

        assertThat(two).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-0002", "C-0003");
        assertThat(four).extracting(ScoredCampaign::id).containsExactly("C-0002");
    }

    @Test
    void filtersStillRankWithinTheMatches() {
        var result = retriever.retrieve(new CampaignBrief("baking flour sugar butter", "grocery", null, null, null, null), 3);

        assertThat(result.get(0).id()).isEqualTo("C-0003");
    }

    @Test
    void aCombinationThatMatchesNothingReturnsAnEmptyList() {
        var result = retriever.retrieve(new CampaignBrief("campaign", "pet", "seniors", "video", null, null), 20);

        assertThat(result).isEmpty();
    }

    @Test
    void filterValuesAreCaseInsensitiveAndTrimmed() {
        var result = retriever.retrieve(new CampaignBrief("campaign", "  Grocery ", null, " EMAIL", null, null), 20);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-0002", "C-0003");
    }

    @Test
    void blankFilterValuesAreTreatedAsNotSet() {
        var result = retriever.retrieve(new CampaignBrief("campaign", " ", "", null, null, null), 20);

        assertThat(result).hasSize(SEED.size());
    }

    @Test
    void filterValuesWithQuotesAreRejectedBeforeReachingTheDatabase() {
        var brief = new CampaignBrief("campaign", "beauty\" || 1==1", null, null, null, null);

        assertThatThrownBy(() -> retriever.retrieve(brief, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category");
    }

    @Test
    void scoresAreFiniteCosineSimilarities() {
        var result = retriever.retrieve(brief("winter skincare cream"), 12);

        assertThat(result).allSatisfy(c -> assertThat(c.score()).isBetween(-1.0, 1.0001));
        assertThat(result.get(0).score()).isGreaterThan(result.get(result.size() - 1).score());
    }
}
