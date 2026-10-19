package io.github.dakshshukla.lookalike.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

class ModelRerankerTest {

    private static final CampaignBrief BRIEF = new CampaignBrief("rice and lentils for a pantry", null, null, null, null, null);

    private static ScoredCampaign candidate(String id, String text, double score) {
        return new ScoredCampaign(
                new Campaign(id, "Campaign " + id, "grocery", "families", "video", "awareness", 1000, 14, 1000, 0.01, 2.0, text), score);
    }

    private final List<ScoredCampaign> candidates = List.of(
            candidate("C-1", "Garden hoses.", 0.9),
            candidate("C-2", "Rice and lentils for the pantry.", 0.5),
            candidate("C-3", "Bread and butter.", 0.4));

    private static ChatModel replying(String reply) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
            }
        };
    }

    private static ChatModel throwing() {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new IllegalStateException("provider down");
            }
        };
    }

    private ModelReranker reranker(ChatModel model) {
        return new ModelReranker(ChatClient.create(model), new LexicalCrossScorerReranker(), 0.3, 0.7);
    }

    @Test
    void reordersByTheModelScores() {
        var model = replying("""
                {"scores":[{"campaignId":"C-1","score":1},{"campaignId":"C-2","score":9.5},{"campaignId":"C-3","score":4}]}
                """);

        List<ScoredCampaign> result = reranker(model).rerank(BRIEF, candidates);

        assertThat(result).extracting(ScoredCampaign::id).containsExactly("C-2", "C-3", "C-1");
        assertThat(result.get(0).score()).isEqualTo(0.3 * 0.5 + 0.7 * 0.95, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void ignoresIdsThatWereNotCandidates() {
        var model = replying("""
                {"scores":[{"campaignId":"C-404","score":10},{"campaignId":"C-2","score":8}]}
                """);

        List<ScoredCampaign> result = reranker(model).rerank(BRIEF, candidates);

        assertThat(result).extracting(ScoredCampaign::id).containsExactlyInAnyOrder("C-1", "C-2", "C-3");
        assertThat(result.get(0).id()).isEqualTo("C-2");
    }

    @Test
    void candidatesTheModelSkippedGetTheLexicalScore() {
        var model = replying("{\"scores\":[{\"campaignId\":\"C-2\",\"score\":8}]}");
        var lexical = new LexicalCrossScorerReranker().rerank(BRIEF, candidates);

        List<ScoredCampaign> result = reranker(model).rerank(BRIEF, candidates);

        assertThat(result).hasSize(3);
        double lexicalScoreOfC1 = lexical.stream().filter(c -> c.id().equals("C-1")).findFirst().orElseThrow().score();
        assertThat(result.stream().filter(c -> c.id().equals("C-1")).findFirst().orElseThrow().score())
                .isEqualTo(lexicalScoreOfC1);
    }

    @Test
    void fallsBackToTheLexicalResultOnGarbageOutput() {
        List<ScoredCampaign> result = reranker(replying("I think the second one is best.")).rerank(BRIEF, candidates);

        assertThat(result).isEqualTo(new LexicalCrossScorerReranker().rerank(BRIEF, candidates));
    }

    @Test
    void fallsBackToTheLexicalResultWhenTheModelThrows() {
        List<ScoredCampaign> result = reranker(throwing()).rerank(BRIEF, candidates);

        assertThat(result).isEqualTo(new LexicalCrossScorerReranker().rerank(BRIEF, candidates));
        assertThat(result.get(0).id()).isEqualTo("C-2");
    }

    @Test
    void emptyScoreListFallsBack() {
        List<ScoredCampaign> result = reranker(replying("{\"scores\":[]}")).rerank(BRIEF, candidates);

        assertThat(result).isEqualTo(new LexicalCrossScorerReranker().rerank(BRIEF, candidates));
    }

    @Test
    void emptyCandidateListNeverCallsTheModel() {
        assertThat(reranker(throwing()).rerank(BRIEF, List.of())).isEmpty();
    }
}
