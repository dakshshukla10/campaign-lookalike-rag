package io.github.dakshshukla.lookalike.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

class LexicalCrossScorerRerankerTest {

    private final LexicalCrossScorerReranker reranker = new LexicalCrossScorerReranker();

    private static ScoredCampaign candidate(String id, String text, String category, String channel, int budget, double score) {
        return new ScoredCampaign(
                new Campaign(id, "Campaign " + id, category, "families", channel, "awareness", budget, 14, 1000, 0.01, 2.0, text),
                score);
    }

    private static CampaignBrief brief(String description) {
        return new CampaignBrief(description, null, null, null, null, null);
    }

    @Test
    void movesAClearlyBetterTextMatchAheadOfAHigherRetrievalScore() {
        var weak = candidate("C-1", "Garden hoses and sprinklers for lawns.", "home", "video", 1000, 0.60);
        var strong = candidate("C-2", "Rice and lentils to fill a pantry for weekly meals.", "grocery", "video", 1000, 0.50);

        List<ScoredCampaign> result = reranker.rerank(brief("Stock a pantry with rice and lentils"), List.of(weak, strong));

        assertThat(result).extracting(ScoredCampaign::id).containsExactly("C-2", "C-1");
    }

    @Test
    void metadataAgreementLiftsTheMatchingCampaign() {
        var video = candidate("C-1", "Seasonal offers for shoppers.", "toys", "video", 1000, 0.5);
        var email = candidate("C-2", "Seasonal offers for shoppers.", "toys", "email", 1000, 0.5);
        var brief = new CampaignBrief("Seasonal offers", null, null, "email", null, null);

        List<ScoredCampaign> result = reranker.rerank(brief, List.of(video, email));

        assertThat(result.get(0).id()).isEqualTo("C-2");
        assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    }

    @Test
    void metadataIsIgnoredForFieldsTheBriefDoesNotSet() {
        var video = candidate("C-1", "Seasonal offers.", "toys", "video", 1000, 0.5);
        var email = candidate("C-2", "Seasonal offers.", "toys", "email", 1000, 0.5);

        List<ScoredCampaign> result = reranker.rerank(brief("Seasonal offers"), List.of(video, email));

        assertThat(result.get(0).score()).isEqualTo(result.get(1).score());
        assertThat(result).extracting(ScoredCampaign::id).containsExactly("C-1", "C-2");
    }

    @Test
    void closerBudgetWinsWhenTheBriefHasOne() {
        var far = candidate("C-1", "Seasonal offers.", "toys", "video", 400_000, 0.5);
        var near = candidate("C-2", "Seasonal offers.", "toys", "video", 52_000, 0.5);
        var brief = new CampaignBrief("Seasonal offers", null, null, null, null, 50_000);

        assertThat(reranker.rerank(brief, List.of(far, near))).extracting(ScoredCampaign::id).containsExactly("C-2", "C-1");
    }

    @Test
    void budgetDoesNotMatterWhenTheBriefHasNone() {
        var far = candidate("C-1", "Seasonal offers.", "toys", "video", 400_000, 0.5);
        var near = candidate("C-2", "Seasonal offers.", "toys", "video", 52_000, 0.5);

        List<ScoredCampaign> result = reranker.rerank(brief("Seasonal offers"), List.of(far, near));

        assertThat(result.get(0).score()).isEqualTo(result.get(1).score());
    }

    @Test
    void retrievalScoreDecidesWhenCrossScoresTie() {
        var low = candidate("C-1", "Seasonal offers.", "toys", "video", 1000, 0.2);
        var high = candidate("C-2", "Seasonal offers.", "toys", "video", 1000, 0.9);

        assertThat(reranker.rerank(brief("Seasonal offers"), List.of(low, high)))
                .extracting(ScoredCampaign::id).containsExactly("C-2", "C-1");
    }

    @Test
    void returnsEveryCandidateWithScoresInRange() {
        List<ScoredCampaign> input = List.of(
                candidate("C-1", "Pantry rice.", "grocery", "video", 1000, 1.7),
                candidate("C-2", "Dog leash.", "pet", "email", 500_000, -0.2),
                candidate("C-3", "", "home", "social", 10, 0.0));

        List<ScoredCampaign> result = reranker.rerank(
                new CampaignBrief("rice pantry", "grocery", "families", "video", "awareness", 1000), input);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-1", "C-2", "C-3");
        assertThat(result).allSatisfy(c -> assertThat(c.score()).isBetween(0.0, 1.0));
    }

    @Test
    void tiesKeepTheIncomingOrder() {
        var a = candidate("C-1", "Same text.", "toys", "video", 1000, 0.5);
        var b = candidate("C-2", "Same text.", "toys", "video", 1000, 0.5);
        var c = candidate("C-3", "Same text.", "toys", "video", 1000, 0.5);

        assertThat(reranker.rerank(brief("Same text"), List.of(c, a, b)))
                .extracting(ScoredCampaign::id).containsExactly("C-3", "C-1", "C-2");
    }

    @Test
    void handlesEmptyAndSingleLists() {
        assertThat(reranker.rerank(brief("anything"), List.of())).isEmpty();

        var only = candidate("C-1", "Pantry rice.", "grocery", "video", 1000, 0.5);
        List<ScoredCampaign> result = reranker.rerank(brief("pantry rice"), List.of(only));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isBetween(0.0, 1.0);
    }

    @Test
    void weightsCanShiftTheBalanceTowardRetrieval() {
        var retrievalFavourite = candidate("C-1", "Garden hoses.", "home", "video", 1000, 0.95);
        var textFavourite = candidate("C-2", "Rice and lentils for the pantry.", "grocery", "video", 1000, 0.30);
        var retrievalHeavy = new LexicalCrossScorerReranker(0.95, 0.05);

        assertThat(retrievalHeavy.rerank(brief("rice and lentils pantry"), List.of(textFavourite, retrievalFavourite)).get(0).id())
                .isEqualTo("C-1");
        assertThat(reranker.rerank(brief("rice and lentils pantry"), List.of(retrievalFavourite, textFavourite)).get(0).id())
                .isEqualTo("C-2");
    }

    @Test
    void rejectsUnusableWeights() {
        assertThatThrownBy(() -> new LexicalCrossScorerReranker(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LexicalCrossScorerReranker(-1, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
