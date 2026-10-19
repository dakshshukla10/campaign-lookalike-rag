package io.github.dakshshukla.lookalike.feedback;

import java.util.Collection;
import java.util.Map;

/** Keeps reports of how recommended campaigns worked out and summarises them per campaign. */
public interface FeedbackStore {

    void record(String campaignId, boolean helpful);

    /** Counts for the given campaigns. Campaigns without any report are left out of the map. */
    Map<String, OutcomeStats> statsFor(Collection<String> campaignIds);

    /** Test and replay helper: removes every recorded outcome. */
    void clear();
}
