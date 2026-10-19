package io.github.dakshshukla.lookalike.rerank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;

/**
 * Asks a chat model to score each candidate from 0 to 10 and blends that with the retrieval
 * score. Anything the model leaves out, or the whole call if it fails, is scored by the
 * fallback reranker instead, so a flaky provider cannot empty the candidate list.
 */
public class ModelReranker implements Reranker {

    private static final Logger log = LoggerFactory.getLogger(ModelReranker.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    static final String TEMPLATE = """
            You score how comparable past ad campaigns are to a new campaign brief.
            Give every candidate a relevance score from 0 (unrelated) to 10 (very close), using only
            the fields shown. Return one score per candidate id and no other ids.
            BRIEF_JSON: {brief}
            CANDIDATES_JSON: {candidates}
            """;

    private final ChatClient chatClient;
    private final Reranker fallback;
    private final double retrievalWeight;
    private final double modelWeight;

    public ModelReranker(ChatClient chatClient, Reranker fallback, double retrievalWeight, double modelWeight) {
        this.chatClient = chatClient;
        this.fallback = fallback;
        double total = retrievalWeight + modelWeight;
        this.retrievalWeight = retrievalWeight / total;
        this.modelWeight = modelWeight / total;
    }

    @Override
    public List<ScoredCampaign> rerank(CampaignBrief brief, List<ScoredCampaign> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        List<ScoredCampaign> lexical = fallback.rerank(brief, candidates);
        Map<String, Double> modelScores;
        try {
            modelScores = askModel(brief, candidates);
        } catch (RuntimeException e) {
            log.warn("Model reranking failed, using the fallback reranker: {}", e.toString());
            return lexical;
        }
        if (modelScores.isEmpty()) {
            log.warn("Model returned no usable scores, using the fallback reranker");
            return lexical;
        }

        Map<String, ScoredCampaign> lexicalById = new HashMap<>();
        lexical.forEach(c -> lexicalById.put(c.id(), c));
        List<ScoredCampaign> result = new ArrayList<>();
        for (ScoredCampaign candidate : candidates) {
            Double modelScore = modelScores.get(candidate.id());
            if (modelScore == null) {
                result.add(lexicalById.get(candidate.id()));
            } else {
                double retrieval = Math.max(0.0, Math.min(1.0, candidate.score()));
                result.add(candidate.withScore(retrievalWeight * retrieval + modelWeight * modelScore / 10.0));
            }
        }
        result.sort((a, b) -> Double.compare(b.score(), a.score()));
        return result;
    }

    private Map<String, Double> askModel(CampaignBrief brief, List<ScoredCampaign> candidates) {
        List<Map<String, Object>> view = candidates.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("name", c.campaign().name());
            m.put("category", c.campaign().category());
            m.put("audience", c.campaign().audience());
            m.put("channel", c.campaign().channel());
            m.put("objective", c.campaign().objective());
            m.put("budgetUsd", c.campaign().budgetUsd());
            m.put("description", c.campaign().description());
            return m;
        }).toList();
        String briefJson;
        String candidatesJson;
        try {
            briefJson = JSON.writeValueAsString(brief);
            candidatesJson = JSON.writeValueAsString(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        RerankPayload payload = chatClient.prompt()
                .user(user -> user.text(TEMPLATE).param("brief", briefJson).param("candidates", candidatesJson))
                .call()
                .entity(RerankPayload.class);

        Map<String, Double> scores = new HashMap<>();
        if (payload != null && payload.scores() != null) {
            var known = candidates.stream().map(ScoredCampaign::id).toList();
            for (RerankPayload.Item item : payload.scores()) {
                if (item != null && item.campaignId() != null && known.contains(item.campaignId())
                        && Double.isFinite(item.score())) {
                    scores.put(item.campaignId(), Math.max(0.0, Math.min(10.0, item.score())));
                }
            }
        }
        return scores;
    }
}
