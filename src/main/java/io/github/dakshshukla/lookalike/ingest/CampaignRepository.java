package io.github.dakshshukla.lookalike.ingest;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import io.github.dakshshukla.lookalike.domain.Campaign;

/** Plain JDBC access to the {@code campaigns} table. Vectors live in the Spring AI vector store. */
@Repository
public class CampaignRepository {

    private static final RowMapper<Campaign> MAPPER = (rs, row) -> new Campaign(
            rs.getString("campaign_id"),
            rs.getString("name"),
            rs.getString("category"),
            rs.getString("audience"),
            rs.getString("channel"),
            rs.getString("objective"),
            rs.getInt("budget_usd"),
            rs.getInt("duration_days"),
            rs.getLong("impressions"),
            rs.getDouble("ctr"),
            rs.getDouble("roas"),
            rs.getString("description"));

    private final JdbcTemplate jdbc;

    public CampaignRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts or updates by id, so loading the same file twice is harmless. */
    public void upsert(Campaign c) {
        jdbc.update("""
                INSERT INTO campaigns (campaign_id, name, category, audience, channel, objective,
                                       budget_usd, duration_days, impressions, ctr, roas, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (campaign_id) DO UPDATE SET
                    name = EXCLUDED.name, category = EXCLUDED.category, audience = EXCLUDED.audience,
                    channel = EXCLUDED.channel, objective = EXCLUDED.objective,
                    budget_usd = EXCLUDED.budget_usd, duration_days = EXCLUDED.duration_days,
                    impressions = EXCLUDED.impressions, ctr = EXCLUDED.ctr, roas = EXCLUDED.roas,
                    description = EXCLUDED.description
                """,
                c.id(), c.name(), c.category(), c.audience(), c.channel(), c.objective(),
                c.budgetUsd(), c.durationDays(), c.impressions(), c.ctr(), c.roas(), c.description());
    }

    public Map<String, Campaign> findByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Campaign> found = jdbc.query(
                "SELECT * FROM campaigns WHERE campaign_id IN (" + placeholders + ")", MAPPER, ids.toArray());
        return found.stream().collect(Collectors.toMap(Campaign::id, Function.identity()));
    }

    public List<Campaign> findAll() {
        return jdbc.query("SELECT * FROM campaigns ORDER BY campaign_id", MAPPER);
    }

    public int count() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM campaigns", Integer.class);
        return count == null ? 0 : count;
    }

    public boolean exists(String campaignId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM campaigns WHERE campaign_id = ?", Integer.class, campaignId);
        return n != null && n > 0;
    }
}
