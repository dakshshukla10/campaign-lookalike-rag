package io.github.dakshshukla.lookalike.rerank;

import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Citation;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.Violation;

/**
 * Ranks through a Spring AI {@link ChatClient} and converts the reply into
 * {@link RankingPayload} with the structured output converter. Which model answers is decided
 * by the {@code ChatModel} bean: the offline model by default, an OpenAI-compatible one when
 * the {@code openai} profile is on.
 */
public class LlmRecommendationRanker implements RecommendationRanker {

    private final ChatClient chatClient;

    public LlmRecommendationRanker(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public List<Recommendation> rank(CampaignBrief brief, List<ScoredCampaign> rerankedCandidates, int limit) {
        return ask(brief, rerankedCandidates, limit, List.of());
    }

    @Override
    public List<Recommendation> retry(
            CampaignBrief brief, List<ScoredCampaign> rerankedCandidates, int limit, List<Violation> violations) {
        return ask(brief, rerankedCandidates, limit, violations);
    }

    private List<Recommendation> ask(
            CampaignBrief brief, List<ScoredCampaign> candidates, int limit, List<Violation> violations) {
        var variables = RankingPrompt.variables(brief, candidates, limit, violations);
        RankingPayload payload = chatClient.prompt()
                .user(user -> {
                    user.text(RankingPrompt.TEMPLATE);
                    variables.forEach(user::param);
                })
                .call()
                .entity(RankingPayload.class);
        return toRecommendations(payload, limit);
    }

    static List<Recommendation> toRecommendations(RankingPayload payload, int limit) {
        if (payload == null || payload.recommendations() == null) {
            return List.of();
        }
        return payload.recommendations().stream()
                .filter(Objects::nonNull)
                .limit(limit)
                .map(item -> new Recommendation(
                        item.rank(),
                        item.summary(),
                        item.citations() == null
                                ? List.of()
                                : item.citations().stream()
                                        .filter(Objects::nonNull)
                                        .map(c -> new Citation(c.campaignId(), c.reason()))
                                        .toList()))
                .toList();
    }
}
