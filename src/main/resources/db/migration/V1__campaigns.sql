CREATE TABLE campaigns (
    campaign_id   text PRIMARY KEY,
    name          text             NOT NULL,
    category      text             NOT NULL,
    audience      text             NOT NULL,
    channel       text             NOT NULL,
    objective     text             NOT NULL,
    budget_usd    integer          NOT NULL,
    duration_days integer          NOT NULL,
    impressions   bigint           NOT NULL,
    ctr           double precision NOT NULL,
    roas          double precision NOT NULL,
    description   text             NOT NULL DEFAULT ''
);

CREATE INDEX campaigns_category_idx ON campaigns (category);
