package io.github.dakshshukla.lookalike.domain;

import java.util.List;

/**
 * One ranked lookalike recommendation. Every recommendation must carry at least one
 * {@link Citation}.
 */
public record Recommendation(int rank, String summary, List<Citation> citations) {

    public Recommendation {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
