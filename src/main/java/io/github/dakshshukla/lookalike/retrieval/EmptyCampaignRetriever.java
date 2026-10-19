package io.github.dakshshukla.lookalike.retrieval;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;

/** Phase 1 placeholder: retrieves nothing. Replaced by the pgvector retriever in Phase 2. */
@Component
public class EmptyCampaignRetriever implements CampaignRetriever {

    @Override
    public List<Campaign> retrieve(CampaignBrief brief, int topK) {
        return List.of();
    }
}
