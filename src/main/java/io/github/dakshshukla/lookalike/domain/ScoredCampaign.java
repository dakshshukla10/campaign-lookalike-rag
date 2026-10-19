package io.github.dakshshukla.lookalike.domain;

/** A candidate campaign together with the score of the stage that produced it. */
public record ScoredCampaign(Campaign campaign, double score) {

    public String id() {
        return campaign.id();
    }

    public ScoredCampaign withScore(double newScore) {
        return new ScoredCampaign(campaign, newScore);
    }
}
