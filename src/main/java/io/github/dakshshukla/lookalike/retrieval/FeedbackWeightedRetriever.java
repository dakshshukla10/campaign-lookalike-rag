package io.github.dakshshukla.lookalike.retrieval;

import java.util.List;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.feedback.FeedbackStore;
import io.github.dakshshukla.lookalike.feedback.OutcomeWeights;

/**
 * Re-weights the scores of another retriever with recorded outcomes. It asks for more candidates
 * than needed so a campaign just outside the top K can move in, then cuts back to K.
 */
public class FeedbackWeightedRetriever implements CampaignRetriever {

    static final int OVERFETCH_FACTOR = 3;

    private final CampaignRetriever delegate;
    private final FeedbackStore store;
    private final double strength;

    public FeedbackWeightedRetriever(CampaignRetriever delegate, FeedbackStore store, double strength) {
        this.delegate = delegate;
        this.store = store;
        this.strength = strength;
    }

    @Override
    public List<ScoredCampaign> retrieve(CampaignBrief brief, int topK) {
        List<ScoredCampaign> candidates = delegate.retrieve(brief, topK * OVERFETCH_FACTOR);
        var stats = store.statsFor(candidates.stream().map(ScoredCampaign::id).toList());
        List<ScoredCampaign> weighted = OutcomeWeights.apply(candidates, stats, strength);
        return weighted.size() > topK ? weighted.subList(0, topK) : weighted;
    }
}
