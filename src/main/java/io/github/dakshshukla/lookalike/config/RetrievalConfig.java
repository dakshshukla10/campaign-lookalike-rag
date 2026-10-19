package io.github.dakshshukla.lookalike.config;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.dakshshukla.lookalike.feedback.FeedbackStore;
import io.github.dakshshukla.lookalike.ingest.CampaignRepository;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;
import io.github.dakshshukla.lookalike.retrieval.FeedbackWeightedRetriever;
import io.github.dakshshukla.lookalike.retrieval.PgVectorCampaignRetriever;

@Configuration
public class RetrievalConfig {

    /**
     * Plain vector search, wrapped with outcome re-weighting only when
     * {@code lookalike.feedback.reweight.enabled=true}.
     */
    @Bean
    CampaignRetriever campaignRetriever(
            VectorStore vectorStore,
            CampaignRepository repository,
            FeedbackStore feedbackStore,
            @Value("${lookalike.feedback.reweight.enabled:false}") boolean reweight,
            @Value("${lookalike.feedback.reweight.strength:0.05}") double strength) {
        CampaignRetriever vectorSearch = new PgVectorCampaignRetriever(vectorStore, repository);
        return reweight ? new FeedbackWeightedRetriever(vectorSearch, feedbackStore, strength) : vectorSearch;
    }
}
