package io.github.dakshshukla.lookalike.ingest;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fills a fresh database with the bundled sample CSV when {@code lookalike.seed.enabled=true}.
 * It only acts when the campaigns table is empty, so restarts and a second instance leave
 * existing data alone. The load goes through the ingest service's transactional method, which
 * keeps the table and the vector store in step if the embedding step fails.
 */
@Component
@ConditionalOnProperty(name = "lookalike.seed.enabled", havingValue = "true")
public class SampleDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataSeeder.class);

    private final CampaignRepository repository;
    private final CampaignIngestService ingest;
    private final Path csv;

    public SampleDataSeeder(
            CampaignRepository repository,
            CampaignIngestService ingest,
            @Value("${lookalike.seed.path:data/sample_campaigns.csv}") String path) {
        this.repository = repository;
        this.ingest = ingest;
        this.csv = Path.of(path);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int existing = repository.count();
        if (existing > 0) {
            log.info("Skipping the sample data load, campaigns already holds {} rows", existing);
            return;
        }
        int loaded = ingest.ingest(CsvCampaignReader.read(csv));
        log.info("Loaded {} sample campaigns from {}", loaded, csv);
    }
}
