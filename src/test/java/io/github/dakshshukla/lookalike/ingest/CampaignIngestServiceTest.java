package io.github.dakshshukla.lookalike.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.support.IntegrationTest;

@IntegrationTest
class CampaignIngestServiceTest {

    private static final Path SAMPLE = Path.of("data", "sample_campaigns.csv");

    @Autowired
    private CampaignIngestService service;

    @Autowired
    private CampaignRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.execute("TRUNCATE campaigns, vector_store");
    }

    @Test
    void loadsEveryCsvRowIntoPostgresAndTheVectorStore() throws Exception {
        List<Campaign> expected = CsvCampaignReader.read(SAMPLE);

        int loaded = service.ingest(SAMPLE);

        assertThat(loaded).isEqualTo(expected.size());
        assertThat(repository.count()).isEqualTo(expected.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isEqualTo(expected.size());
        assertThat(repository.findByIds(List.of(expected.get(0).id())).get(expected.get(0).id()))
                .isEqualTo(expected.get(0));
    }

    @Test
    void loadingTheSameFileTwiceDoesNotDuplicateRows() throws Exception {
        service.ingest(SAMPLE);
        service.ingest(SAMPLE);

        assertThat(repository.count()).isEqualTo(CsvCampaignReader.read(SAMPLE).size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vector_store", Integer.class))
                .isEqualTo(repository.count());
    }

    @Test
    void storesVectorsWithTheConfiguredDimension() throws Exception {
        service.ingest(SAMPLE);

        Integer dims = jdbc.queryForObject("SELECT vector_dims(embedding) FROM vector_store LIMIT 1", Integer.class);

        assertThat(dims).isEqualTo(256);
    }

    @Test
    void updatesAChangedCampaignInPlace() {
        Campaign original = new Campaign("C-9001", "Old name", "pet", "families", "email", "retention", 1000, 7, 10, 0.01, 1.0, "old text");
        Campaign changed = new Campaign("C-9001", "New name", "pet", "families", "email", "retention", 1000, 7, 10, 0.01, 1.0, "new text");

        service.ingest(List.of(original));
        service.ingest(List.of(changed));

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByIds(List.of("C-9001")).get("C-9001").name()).isEqualTo("New name");
    }
}
