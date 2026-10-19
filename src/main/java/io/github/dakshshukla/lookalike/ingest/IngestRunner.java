package io.github.dakshshukla.lookalike.ingest;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the ingestion at startup when {@code lookalike.ingest.path} is set, for example
 * {@code ./gradlew ingest}. Nothing is loaded when the property is empty.
 */
@Component
@ConditionalOnProperty(name = "lookalike.ingest.path")
public class IngestRunner implements ApplicationRunner {

    private final CampaignIngestService service;
    private final String path;

    public IngestRunner(CampaignIngestService service, @Value("${lookalike.ingest.path}") String path) {
        this.service = service;
        this.path = path;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        int count = service.ingest(Path.of(path));
        System.out.println("Loaded " + count + " campaigns from " + path);
    }
}
