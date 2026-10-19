package io.github.dakshshukla.lookalike.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.grounding.Violation;
import io.github.dakshshukla.lookalike.rerank.PassthroughReranker;
import io.github.dakshshukla.lookalike.rerank.RecommendationRanker;

class LookalikeServiceTest {

    private static final CampaignBrief BRIEF = new CampaignBrief("holiday toys", null, null, null, null, null);

    private static ScoredCampaign candidate(String id, double score) {
        return new ScoredCampaign(
                new Campaign(id, "name " + id, "toys", "families", "video", "awareness", 1000, 7, 1000, 0.01, 2.0, "text"),
                score);
    }

    private final List<ScoredCampaign> candidates =
            List.of(candidate("C-1", 0.9), candidate("C-2", 0.8), candidate("C-3", 0.7), candidate("C-4", 0.6));

    private static Recommendation rec(int rank, String... ids) {
        return new Recommendation(rank, "summary " + rank,
                java.util.Arrays.stream(ids).map(id -> new Citation(id, "reason")).toList());
    }

    /** Ranker whose answers are scripted per call; records what the retry was told. */
    private static final class ScriptedRanker implements RecommendationRanker {
        final List<Function<Integer, List<Recommendation>>> script;
        final List<List<Violation>> retryViolations = new ArrayList<>();
        int calls;

        @SafeVarargs
        ScriptedRanker(Function<Integer, List<Recommendation>>... script) {
            this.script = List.of(script);
        }

        @Override
        public List<Recommendation> rank(CampaignBrief brief, List<ScoredCampaign> c, int limit) {
            return script.get(Math.min(calls++, script.size() - 1)).apply(calls);
        }

        @Override
        public List<Recommendation> retry(CampaignBrief brief, List<ScoredCampaign> c, int limit, List<Violation> v) {
            retryViolations.add(v);
            return rank(brief, c, limit);
        }
    }

    private LookalikeService service(RecommendationRanker ranker, int maxAttempts, List<ScoredCampaign> retrieved) {
        return new LookalikeService(
                (brief, topK) -> retrieved, new PassthroughReranker(), ranker, new GroundingValidator(), maxAttempts);
    }

    @Test
    void keepsAGroundedAnswerWithoutRetrying() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-1"), rec(2, "C-2"), rec(3, "C-3")));

        PipelineResult result = service(ranker, 2, candidates).run(BRIEF);

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.firstAttempt().fullyGrounded()).isTrue();
        assertThat(result.notes()).isEmpty();
    }

    @Test
    void retriesWithTheRejectedIdsAndKeepsTheCleanSecondAnswer() {
        var ranker = new ScriptedRanker(
                n -> List.of(rec(1, "C-1"), rec(2, "C-999")),
                n -> List.of(rec(1, "C-1"), rec(2, "C-2"), rec(3, "C-3")));

        PipelineResult result = service(ranker, 2, candidates).run(BRIEF);

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.recommendations()).extracting(r -> r.citations().get(0).campaignId())
                .containsExactly("C-1", "C-2", "C-3");
        assertThat(result.firstAttempt().violations()).extracting(Violation::citedId).containsExactly("C-999");
        assertThat(ranker.retryViolations).hasSize(1);
        assertThat(ranker.retryViolations.get(0)).extracting(Violation::citedId).containsExactly("C-999");
        assertThat(result.notes()).anyMatch(n -> n.contains("again") && n.contains("C-999"));
    }

    @Test
    void fillsMissingSlotsFromRerankedOrderWhenTheModelKeepsInventingIds() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-1"), rec(2, "C-404"), rec(3, "C-405")));

        PipelineResult result = service(ranker, 2, candidates).run(BRIEF);

        assertThat(result.status()).isEqualTo("degraded");
        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.recommendations()).extracting(Recommendation::rank).containsExactly(1, 2, 3);
        assertThat(result.recommendations()).extracting(Recommendation::source)
                .containsExactly(Recommendation.Source.MODEL, Recommendation.Source.FALLBACK, Recommendation.Source.FALLBACK);
        // The fallback never repeats a campaign the model already cited.
        assertThat(result.recommendations()).extracting(r -> r.citations().get(0).campaignId())
                .containsExactly("C-1", "C-2", "C-3");
        assertThat(result.finalGrounding().citationsGrounded()).isEqualTo(1);
    }

    @Test
    void everyCitationInTheFinalAnswerWasRetrieved() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-1", "C-777"), rec(2, "C-2")));

        PipelineResult result = service(ranker, 3, candidates).run(BRIEF);

        var retrievedIds = candidates.stream().map(ScoredCampaign::id).toList();
        assertThat(result.recommendations())
                .flatExtracting(Recommendation::citations)
                .extracting(Citation::campaignId)
                .allMatch(retrievedIds::contains);
        assertThat(ranker.calls).isEqualTo(3);
    }

    @Test
    void fallsBackWhenTheRankerThrows() {
        RecommendationRanker broken = (brief, c, limit) -> {
            throw new IllegalStateException("provider down");
        };

        PipelineResult result = service(broken, 2, candidates).run(BRIEF);

        assertThat(result.status()).isEqualTo("degraded");
        assertThat(result.recommendations()).hasSize(3);
        assertThat(result.recommendations()).allMatch(r -> r.source() == Recommendation.Source.FALLBACK);
        assertThat(result.notes()).anyMatch(n -> n.contains("ranker call failed"));
    }

    @Test
    void doesNotRetryWhenOnlyOneAttemptIsAllowed() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-9")));

        PipelineResult result = service(ranker, 1, candidates).run(BRIEF);

        assertThat(ranker.calls).isEqualTo(1);
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.fallbackUsed()).isTrue();
    }

    @Test
    void doesNotInventFillWhenThereAreFewerCandidatesThanSlots() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-9")));

        PipelineResult result = service(ranker, 2, candidates.subList(0, 2)).run(BRIEF);

        assertThat(result.recommendations()).hasSize(2);
    }

    @Test
    void reportsNoMatchesWithoutCallingTheRanker() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-1")));

        PipelineResult result = service(ranker, 2, List.of()).run(BRIEF);

        assertThat(result.status()).isEqualTo("no_matches");
        assertThat(result.recommendations()).isEmpty();
        assertThat(ranker.calls).isZero();
    }

    @Test
    void responseCarriesTheViolationsThatSurvivedTheRetries() {
        var ranker = new ScriptedRanker(n -> List.of(rec(1, "C-1"), rec(2, "C-404")));

        LookalikeResponse response = service(ranker, 2, candidates).lookalikes(BRIEF);

        assertThat(response.status()).isEqualTo("degraded");
        assertThat(response.grounding().rejected()).extracting(Violation::citedId).containsExactly("C-404");
        assertThat(response.retrievedCount()).isEqualTo(4);
    }
}
