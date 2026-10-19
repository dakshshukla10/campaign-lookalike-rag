package io.github.dakshshukla.lookalike;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import io.github.dakshshukla.lookalike.api.LookalikeService;
import io.github.dakshshukla.lookalike.api.PipelineResult;
import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.Recommendation;
import io.github.dakshshukla.lookalike.ingest.CampaignIngestService;
import io.github.dakshshukla.lookalike.support.FakeOpenAiServer;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

/**
 * Runs the whole pipeline with the {@code openai} profile against a local stand-in for an
 * OpenAI-compatible server, to show the Spring AI provider path (embeddings, chat, structured
 * output parsing, grounding, retry) works end to end. Nothing here proves quality, only wiring.
 */
@IntegrationTest
@ActiveProfiles("openai")
class OpenAiProviderPipelineTest {

    private static final FakeOpenAiServer SERVER = startServer();

    private static FakeOpenAiServer startServer() {
        try {
            return new FakeOpenAiServer(true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", SERVER::baseUrl);
        registry.add("spring.ai.openai.api-key", () -> "test-key");
        registry.add("lookalike.ai.embedding-dimensions", () -> FakeOpenAiServer.EMBEDDING_DIMENSIONS);
        // The shared test database already holds a 256-wide vector table from the offline tests.
        registry.add("spring.ai.vectorstore.pgvector.table-name", () -> "vector_store_provider_test");
        registry.add("lookalike.cache.enabled", () -> "false");
        registry.add("lookalike.rerank.mode", () -> "none");
    }

    @AfterAll
    static void stopServer() {
        SERVER.close();
    }

    @Autowired
    private LookalikeService service;

    @Autowired
    private CampaignIngestService ingest;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE campaigns");
        jdbc.execute("TRUNCATE vector_store_provider_test");
        ingest.ingest(List.of(
                new Campaign("C-1001", "Pantry push", "grocery", "families", "video", "awareness", 1000, 7, 10, 0.01, 2.0, "rice and lentils"),
                new Campaign("C-1002", "Pantry email", "grocery", "families", "email", "retention", 1000, 7, 10, 0.01, 2.0, "flour and sugar"),
                new Campaign("C-1003", "Dog walks", "pet", "seniors", "social", "conversion", 1000, 7, 10, 0.01, 2.0, "leashes")));
    }

    @Test
    void embedsRanksAndRetriesThroughTheProviderClient() {
        PipelineResult result = service.run(new CampaignBrief("pantry staples", "grocery", null, null, null, null));

        assertThat(SERVER.embeddingCalls()).isPositive();
        assertThat(result.retrieved()).hasSize(2);
        // The stand-in invents an id on its first answer; the validator rejects it and the retry fixes it.
        assertThat(result.firstAttempt().violations()).hasSize(1);
        assertThat(result.attempts()).isEqualTo(2);
        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.recommendations()).hasSize(2);
        assertThat(result.recommendations()).allMatch(r -> r.source() == Recommendation.Source.MODEL);
        assertThat(result.recommendations()).flatExtracting(Recommendation::citations)
                .extracting(c -> c.campaignId()).containsExactlyInAnyOrder("C-1001", "C-1002");
        assertThat(SERVER.chatBodies().get(1)).contains("REJECTED_IDS");
    }
}
