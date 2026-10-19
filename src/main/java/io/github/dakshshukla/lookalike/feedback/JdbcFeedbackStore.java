package io.github.dakshshukla.lookalike.feedback;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFeedbackStore implements FeedbackStore {

    private final JdbcTemplate jdbc;

    public JdbcFeedbackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(String campaignId, boolean helpful) {
        jdbc.update("INSERT INTO outcomes (campaign_id, helpful) VALUES (?, ?)", campaignId, helpful);
    }

    @Override
    public Map<String, OutcomeStats> statsFor(Collection<String> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", campaignIds.stream().map(id -> "?").toList());
        Map<String, OutcomeStats> stats = new HashMap<>();
        jdbc.query(
                "SELECT campaign_id, count(*) FILTER (WHERE helpful) AS helpful, count(*) AS total"
                        + " FROM outcomes WHERE campaign_id IN (" + placeholders + ") GROUP BY campaign_id",
                rs -> {
                    stats.put(rs.getString("campaign_id"), new OutcomeStats(rs.getInt("helpful"), rs.getInt("total")));
                },
                campaignIds.toArray());
        return stats;
    }

    @Override
    public void clear() {
        jdbc.update("DELETE FROM outcomes");
    }
}
