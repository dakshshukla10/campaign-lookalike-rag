package io.github.dakshshukla.lookalike.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import io.github.dakshshukla.lookalike.ai.OfflineChatModel;
import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.GroundingValidator;
import io.github.dakshshukla.lookalike.grounding.Violation;

class LlmRecommendationRankerTest {

    private static final CampaignBrief BRIEF = new CampaignBrief(
            "Lentils and rice for pantry meals", "grocery", null, "video", null, 40000);

    private static ScoredCampaign candidate(String id, String name, String category, String description, double score) {
        return new ScoredCampaign(
                new Campaign(id, name, category, "families", "video", "awareness", 45000, 14, 900000, 0.0123, 3.4, description),
                score);
    }

    private final List<ScoredCampaign> candidates = List.of(
            candidate("C-0001", "Winter pantry staples awareness push", "grocery", "Fill the pantry with rice and lentils.", 0.82),
            candidate("C-0002", "Spring dog walks push", "pet", "Leashes for daily walks.", 0.31),
            candidate("C-0003", "Holiday baking conversion push", "grocery", "Flour and sugar for baking.", 0.22),
            candidate("C-0004", "Weekend skincare push", "beauty", "Cleanser and serum.", 0.10));

    /** Chat model that records the prompts it receives and replies with fixed text. */
    private static final class CannedChatModel implements ChatModel {
        final List<String> prompts = new ArrayList<>();
        final String reply;

        CannedChatModel(String reply) {
            this.reply = reply;
        }

        @Override
        public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
            prompts.add(prompt.getContents());
            return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))));
        }
    }

    @Test
    void offlineModelAnswersInTheStructuredShapeAndOnlyCitesCandidates() {
        var ranker = new LlmRecommendationRanker(ChatClient.create(new OfflineChatModel()));

        List<Recommendation> recs = ranker.rank(BRIEF, candidates, 3);

        assertThat(recs).extracting(Recommendation::rank).containsExactly(1, 2, 3);
        assertThat(recs).extracting(r -> r.citations().get(0).campaignId()).containsExactly("C-0001", "C-0002", "C-0003");
        assertThat(recs.get(0).summary()).contains("Winter pantry staples awareness push").contains("ROAS 3.4");
        assertThat(recs.get(0).citations().get(0).reason()).contains("same category grocery");

        var grounding = new GroundingValidator().validate(recs, candidates.stream().map(ScoredCampaign::campaign).toList());
        assertThat(grounding.fullyGrounded()).isTrue();
    }

    @Test
    void respectsTheLimit() {
        var ranker = new LlmRecommendationRanker(ChatClient.create(new OfflineChatModel()));

        assertThat(ranker.rank(BRIEF, candidates, 1)).hasSize(1);
        assertThat(ranker.rank(BRIEF, candidates.subList(0, 2), 3)).hasSize(2);
    }

    @Test
    void parsesJsonThatArrivesInsideACodeFence() {
        var model = new CannedChatModel("""
                ```json
                {"recommendations":[{"rank":1,"summary":"Looks alike","citations":[{"campaignId":"C-0003","reason":"same aisle"}]}]}
                ```
                """);
        var ranker = new LlmRecommendationRanker(ChatClient.create(model));

        List<Recommendation> recs = ranker.rank(BRIEF, candidates, 3);

        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).citations().get(0).campaignId()).isEqualTo("C-0003");
        assertThat(recs.get(0).source()).isEqualTo(Recommendation.Source.MODEL);
    }

    @Test
    void passesTheCandidatesAndFormatInstructionsToTheModel() {
        var model = new CannedChatModel("{\"recommendations\":[]}");

        new LlmRecommendationRanker(ChatClient.create(model)).rank(BRIEF, candidates, 3);

        String prompt = model.prompts.get(0);
        assertThat(prompt).contains("CANDIDATES_JSON:").contains("\"id\":\"C-0004\"").contains("LIMIT: 3");
        assertThat(prompt).contains("recommendations");
        assertThat(prompt).doesNotContain("REJECTED_IDS:");
    }

    @Test
    void retryNamesTheRejectedIdsInThePrompt() {
        var model = new CannedChatModel("{\"recommendations\":[]}");

        new LlmRecommendationRanker(ChatClient.create(model)).retry(
                BRIEF, candidates, 3, List.of(new Violation(2, "C-404", Violation.Reason.NOT_RETRIEVED)));

        assertThat(model.prompts.get(0)).contains("REJECTED_IDS:").contains("C-404 (NOT_RETRIEVED)");
    }

    @Test
    void anAnswerThatIsNotJsonThrowsSoThePipelineCanFallBack() {
        var ranker = new LlmRecommendationRanker(ChatClient.create(new CannedChatModel("Sure! Here are some campaigns.")));

        assertThatThrownBy(() -> ranker.rank(BRIEF, candidates, 3)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void missingCitationListsComeThroughAsEmptyAndGetRejectedByTheValidator() {
        var model = new CannedChatModel("{\"recommendations\":[{\"rank\":1,\"summary\":\"No sources\"}]}");

        List<Recommendation> recs = new LlmRecommendationRanker(ChatClient.create(model)).rank(BRIEF, candidates, 3);

        assertThat(recs.get(0).citations()).isEmpty();
        var grounding = new GroundingValidator().validate(recs, candidates.stream().map(ScoredCampaign::campaign).toList());
        assertThat(grounding.accepted()).isEmpty();
    }
}
