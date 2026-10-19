package io.github.dakshshukla.lookalike.rerank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.text.TextAnalyzer;

/**
 * Offline stand-in for a cross-encoder. Like one, it reads the brief and a campaign together and
 * scores the pair, but with hand-set features instead of a learned model:
 * <ul>
 *   <li>idf-weighted share of the brief's words found in the campaign text (weight 0.45);</li>
 *   <li>share of the brief's word pairs found in the campaign text (0.15);</li>
 *   <li>metadata agreement on category, audience, channel and objective, counted only for the
 *       fields the brief sets (0.25);</li>
 *   <li>budget closeness on a log scale, only when the brief has a budget (0.15).</li>
 * </ul>
 * Features that do not apply are left out and the rest are renormalised. The final score blends
 * this with the retrieval score, so a strong vector match is not thrown away.
 */
public class LexicalCrossScorerReranker implements Reranker {

    static final double TOKEN_WEIGHT = 0.45;
    static final double BIGRAM_WEIGHT = 0.15;
    static final double METADATA_WEIGHT = 0.25;
    static final double BUDGET_WEIGHT = 0.15;

    private final double retrievalWeight;
    private final double crossWeight;

    public LexicalCrossScorerReranker() {
        this(0.4, 0.6);
    }

    public LexicalCrossScorerReranker(double retrievalWeight, double crossWeight) {
        if (retrievalWeight < 0 || crossWeight < 0 || retrievalWeight + crossWeight == 0) {
            throw new IllegalArgumentException("Weights must be non-negative and not both zero");
        }
        double total = retrievalWeight + crossWeight;
        this.retrievalWeight = retrievalWeight / total;
        this.crossWeight = crossWeight / total;
    }

    @Override
    public List<ScoredCampaign> rerank(CampaignBrief brief, List<ScoredCampaign> candidates) {
        if (candidates.size() < 2) {
            return candidates.stream().map(c -> c.withScore(blend(c.score(), crossScore(brief, c.campaign(), Map.of())))).toList();
        }
        Map<String, Double> idf = inverseDocumentFrequency(candidates);
        List<ScoredCampaign> rescored = new ArrayList<>();
        for (ScoredCampaign candidate : candidates) {
            rescored.add(candidate.withScore(blend(candidate.score(), crossScore(brief, candidate.campaign(), idf))));
        }
        // List.sort is stable, so equal scores keep the retrieval order.
        rescored.sort((a, b) -> Double.compare(b.score(), a.score()));
        return rescored;
    }

    double blend(double retrievalScore, double crossScore) {
        double clamped = Math.max(0.0, Math.min(1.0, retrievalScore));
        return retrievalWeight * clamped + crossWeight * crossScore;
    }

    static double crossScore(CampaignBrief brief, Campaign campaign, Map<String, Double> idf) {
        List<String> briefTokens = TextAnalyzer.tokens(brief.description());
        List<String> campaignTokens = TextAnalyzer.tokens(campaign.searchText());

        double weighted = 0;
        double total = 0;
        Set<String> campaignSet = new HashSet<>(campaignTokens);
        for (String token : new HashSet<>(briefTokens)) {
            double weight = idf.getOrDefault(token, 1.0);
            total += weight;
            if (campaignSet.contains(token)) {
                weighted += weight;
            }
        }
        double tokenOverlap = total == 0 ? 0 : weighted / total;

        Set<String> briefPairs = pairs(briefTokens);
        Set<String> campaignPairs = pairs(campaignTokens);
        long sharedPairs = briefPairs.stream().filter(campaignPairs::contains).count();
        double bigramOverlap = briefPairs.isEmpty() ? 0 : (double) sharedPairs / briefPairs.size();

        double score = TOKEN_WEIGHT * tokenOverlap + BIGRAM_WEIGHT * bigramOverlap;
        double weightUsed = TOKEN_WEIGHT + BIGRAM_WEIGHT;

        int fieldsSet = 0;
        int fieldsMatched = 0;
        for (String[] pair : new String[][] {
            {brief.category(), campaign.category()},
            {brief.audience(), campaign.audience()},
            {brief.channel(), campaign.channel()},
            {brief.objective(), campaign.objective()}}) {
            if (pair[0] != null && !pair[0].isBlank()) {
                fieldsSet++;
                if (pair[0].strip().equalsIgnoreCase(pair[1])) {
                    fieldsMatched++;
                }
            }
        }
        if (fieldsSet > 0) {
            score += METADATA_WEIGHT * fieldsMatched / fieldsSet;
            weightUsed += METADATA_WEIGHT;
        }

        if (brief.budgetUsd() != null && brief.budgetUsd() > 0 && campaign.budgetUsd() > 0) {
            double distance = Math.abs(Math.log((double) brief.budgetUsd() / campaign.budgetUsd()));
            score += BUDGET_WEIGHT * (1 - Math.min(1.0, distance / Math.log(4)));
            weightUsed += BUDGET_WEIGHT;
        }
        return Math.max(0.0, Math.min(1.0, score / weightUsed));
    }

    private static Set<String> pairs(List<String> tokens) {
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i + 1 < tokens.size(); i++) {
            pairs.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return pairs;
    }

    private static Map<String, Double> inverseDocumentFrequency(List<ScoredCampaign> candidates) {
        Map<String, Integer> documentCounts = new HashMap<>();
        for (ScoredCampaign candidate : candidates) {
            for (String token : new HashSet<>(TextAnalyzer.tokens(candidate.campaign().searchText()))) {
                documentCounts.merge(token, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        int n = candidates.size();
        // Smoothed so a word in every candidate still counts a little and an unseen word counts most.
        documentCounts.forEach((token, count) -> idf.put(token, Math.log(1.0 + (double) (n + 1) / (count + 0.5))));
        return idf;
    }
}
