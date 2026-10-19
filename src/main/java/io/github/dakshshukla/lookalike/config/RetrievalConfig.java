package io.github.dakshshukla.lookalike.config;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.dakshshukla.lookalike.ingest.CampaignRepository;
import io.github.dakshshukla.lookalike.retrieval.CampaignRetriever;
import io.github.dakshshukla.lookalike.retrieval.PgVectorCampaignRetriever;

@Configuration
public class RetrievalConfig {

    @Bean
    CampaignRetriever campaignRetriever(VectorStore vectorStore, CampaignRepository repository) {
        return new PgVectorCampaignRetriever(vectorStore, repository);
    }
}
