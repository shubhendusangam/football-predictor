-- ============================================================================
-- V8: Add unique constraint for MERGE upsert on matches table
-- Required by the H2 MERGE KEY(match_date, home_team, away_team) clause.
-- ============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS uk_match_date_teams
    ON matches(match_date, home_team, away_team);

