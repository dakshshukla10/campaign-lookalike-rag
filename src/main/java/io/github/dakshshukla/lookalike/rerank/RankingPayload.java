package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The JSON the ranking model must return. The same shape is produced by the offline model and
 * parsed from a real provider, so there is one code path after the chat call.
 */
public record RankingPayload(
        @JsonPropertyDescription("Recommendations, best first") List<Item> recommendations) {

    public record Item(
            @JsonPropertyDescription("1 for the best match, then 2, 3") int rank,
            @JsonPropertyDescription("Two sentences on why the past campaign is comparable") String summary,
            @JsonPropertyDescription("Past campaigns that support the summary") List<CitationItem> citations) {
    }

    public record CitationItem(
            @JsonPropertyDescription("Exactly the id of a candidate campaign, for example C-0001") String campaignId,
            @JsonPropertyDescription("Which facts of that campaign support the summary") String reason) {
    }
}
