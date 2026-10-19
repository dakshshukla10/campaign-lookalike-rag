package io.github.dakshshukla.lookalike.domain;

import java.util.List;

/**
 * One ranked lookalike recommendation. Every recommendation must carry at least one
 * {@link Citation}. {@code source} says whether the ranker produced it or the pipeline filled
 * the slot from retrieval order after the ranker's output was rejected.
 */
public record Recommendation(int rank, String summary, List<Citation> citations, Source source) {

    public enum Source {
        MODEL,
        FALLBACK
    }

    public Recommendation {
        citations = citations == null ? List.of() : List.copyOf(citations);
        source = source == null ? Source.MODEL : source;
    }

    public Recommendation(int rank, String summary, List<Citation> citations) {
        this(rank, summary, citations, Source.MODEL);
    }

    public Recommendation withRank(int newRank) {
        return new Recommendation(newRank, summary, citations, source);
    }
}
