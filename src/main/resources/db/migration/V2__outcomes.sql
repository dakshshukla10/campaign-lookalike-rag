-- One row per report of how a recommended past campaign worked out for a new campaign.
CREATE TABLE outcomes (
    id          bigserial PRIMARY KEY,
    campaign_id text        NOT NULL REFERENCES campaigns (campaign_id) ON DELETE CASCADE,
    helpful     boolean     NOT NULL,
    reported_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX outcomes_campaign_idx ON outcomes (campaign_id);
