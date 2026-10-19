package io.github.dakshshukla.lookalike.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dakshshukla.lookalike.domain.Campaign;

/**
 * Loads campaigns into Postgres and embeds each one into the vector store. The vector document
 * id is the campaign id, so re-running the load replaces rows instead of duplicating them.
 */
@Service
public class CampaignIngestService {

    private static final Logger log = LoggerFactory.getLogger(CampaignIngestService.class);

    private final CampaignRepository repository;
    private final VectorStore vectorStore;

    public CampaignIngestService(CampaignRepository repository, VectorStore vectorStore) {
        this.repository = repository;
        this.vectorStore = vectorStore;
    }

    public int ingest(Path csv) throws IOException {
        return ingest(CsvCampaignReader.read(csv));
    }

    @Transactional
    public int ingest(List<Campaign> campaigns) {
        campaigns.forEach(repository::upsert);
        vectorStore.add(campaigns.stream().map(CampaignIngestService::toDocument).toList());
        log.info("Ingested {} campaigns", campaigns.size());
        return campaigns.size();
    }

    static Document toDocument(Campaign c) {
        // Metadata keys are what the retriever filters on, so keep them equal to the column names.
        return new Document(c.id(), c.searchText(), Map.of(
                "campaignId", c.id(),
                "category", c.category(),
                "audience", c.audience(),
                "channel", c.channel(),
                "objective", c.objective()));
    }
}
