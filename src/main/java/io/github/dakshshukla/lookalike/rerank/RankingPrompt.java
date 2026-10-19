package io.github.dakshshukla.lookalike.rerank;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dakshshukla.lookalike.domain.CampaignBrief;
import io.github.dakshshukla.lookalike.domain.ScoredCampaign;
import io.github.dakshshukla.lookalike.grounding.Violation;

/**
 * Builds the ranking prompt. The brief and the candidates travel as single-line JSON after a
 * marker so that the offline model can read exactly what a real model would see.
 */
public final class RankingPrompt {

    public static final String BRIEF_MARKER = "BRIEF_JSON:";
    public static final String CANDIDATES_MARKER = "CANDIDATES_JSON:";
    public static final String LIMIT_MARKER = "LIMIT:";
    public static final String REJECTED_MARKER = "REJECTED_IDS:";

    /** Variables are filled by the chat client; there are no literal braces in this text. */
    public static final String TEMPLATE = """
            You help ad planners find comparable past campaigns for a new campaign brief.
            Pick the {limit} candidates that are most comparable to the brief, best first.
            Rules: use only the candidate list below, cite the exact campaign id of every candidate you
            mention, and base every statement on the fields of the cited candidate. Do not invent
            campaigns, ids or numbers.
            {rejected}
            LIMIT: {limit}
            BRIEF_JSON: {brief}
            CANDIDATES_JSON: {candidates}
            """;

    private static final ObjectMapper JSON = new ObjectMapper();

    private RankingPrompt() {
    }

    public static Map<String, Object> variables(
            CampaignBrief brief, List<ScoredCampaign> candidates, int limit, List<Violation> violations) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("limit", limit);
        vars.put("brief", toJson(brief));
        vars.put("candidates", toJson(candidates.stream().map(RankingPrompt::candidateView).toList()));
        vars.put("rejected", rejectedLine(violations));
        return vars;
    }

    private static String rejectedLine(List<Violation> violations) {
        if (violations == null || violations.isEmpty()) {
            return "";
        }
        String ids = violations.stream()
                .map(v -> String.valueOf(v.citedId()) + " (" + v.reason() + ")")
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return REJECTED_MARKER + " your previous answer was rejected for these citations: " + ids
                + ". Cite only ids from CANDIDATES_JSON.";
    }

    private static Map<String, Object> candidateView(ScoredCampaign scored) {
        var c = scored.campaign();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", c.id());
        view.put("name", c.name());
        view.put("category", c.category());
        view.put("audience", c.audience());
        view.put("channel", c.channel());
        view.put("objective", c.objective());
        view.put("budgetUsd", c.budgetUsd());
        view.put("durationDays", c.durationDays());
        view.put("impressions", c.impressions());
        view.put("ctr", c.ctr());
        view.put("roas", c.roas());
        view.put("description", c.description());
        view.put("score", Math.round(scored.score() * 1000.0) / 1000.0);
        return view;
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise prompt data", e);
        }
    }
}
