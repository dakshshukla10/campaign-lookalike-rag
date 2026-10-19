package io.github.dakshshukla.lookalike.retrieval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import io.github.dakshshukla.lookalike.domain.Campaign;
import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.ingest.CampaignRepository;

/**
 * Embeds the brief, filters on whichever of category, audience, channel and objective were
 * given, and returns the nearest campaigns by cosine similarity. Filters are exact matches.
 */
public class PgVectorCampaignRetriever implements CampaignRetriever {

    private static final Pattern SAFE_FILTER_VALUE = Pattern.compile("[a-z0-9 _-]{1,64}");

    private final VectorStore vectorStore;
    private final CampaignRepository repository;

    public PgVectorCampaignRetriever(VectorStore vectorStore, CampaignRepository repository) {
        this.vectorStore = vectorStore;
        this.repository = repository;
    }

    @Override
    public List<ScoredCampaign> retrieve(CampaignBrief brief, int topK) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(brief.searchText())
                .topK(topK)
                // Filtered results should come back even when the words barely overlap.
                .similarityThresholdAll();
        Filter.Expression filter = filterFor(brief);
        if (filter != null) {
            request.filterExpression(filter);
        }

        List<Document> hits = vectorStore.similaritySearch(request.build());
        Map<String, Campaign> campaigns = repository.findByIds(
                hits.stream().map(d -> String.valueOf(d.getMetadata().get("campaignId"))).toList());

        List<ScoredCampaign> result = new ArrayList<>();
        for (Document hit : hits) {
            Campaign campaign = campaigns.get(String.valueOf(hit.getMetadata().get("campaignId")));
            if (campaign != null) {
                result.add(new ScoredCampaign(campaign, hit.getScore() == null ? 0.0 : hit.getScore()));
            }
        }
        return result;
    }

    static Filter.Expression filterFor(CampaignBrief brief) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combined = null;
        Map<String, String> wanted = new LinkedHashMap<>();
        wanted.put("category", brief.category());
        wanted.put("audience", brief.audience());
        wanted.put("channel", brief.channel());
        wanted.put("objective", brief.objective());
        for (var entry : wanted.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            String value = entry.getValue().strip().toLowerCase(Locale.ROOT);
            // The value ends up inside a JSON path, so only plain slugs are allowed through.
            if (!SAFE_FILTER_VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException("Unsupported characters in " + entry.getKey() + " filter");
            }
            FilterExpressionBuilder.Op eq = b.eq(entry.getKey(), value);
            combined = combined == null ? eq : b.and(combined, eq);
        }
        return combined == null ? null : combined.build();
    }
}
