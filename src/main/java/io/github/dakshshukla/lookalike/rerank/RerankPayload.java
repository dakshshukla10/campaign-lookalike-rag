package io.github.dakshshukla.lookalike.rerank;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The JSON a model-backed reranker must return: one relevance score per candidate. */
public record RerankPayload(
        @JsonPropertyDescription("One entry per candidate") List<Item> scores) {

    public record Item(
            @JsonPropertyDescription("Exactly the id of a candidate campaign") String campaignId,
            @JsonPropertyDescription("Relevance to the brief from 0 (unrelated) to 10 (very close)") double score) {
    }
}
