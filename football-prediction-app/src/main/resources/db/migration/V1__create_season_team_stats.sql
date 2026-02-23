-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Create season_team_stats table for Elo rating system
-- Database: H2
-- Date: 2026-02-23
-- ═══════════════════════════════════════════════════════════════════════════════

-- Create the season_team_stats table
CREATE TABLE IF NOT EXISTS season_team_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Season and team identification
    season_id VARCHAR(20) NOT NULL,
    team_id BIGINT NOT NULL,
    team_name VARCHAR(255) NOT NULL,

    -- Match statistics
    matches_played INT NOT NULL DEFAULT 0,
    wins INT NOT NULL DEFAULT 0,
    draws INT NOT NULL DEFAULT 0,
    losses INT NOT NULL DEFAULT 0,

    -- Goals statistics
    goals_scored INT NOT NULL DEFAULT 0,
    goals_conceded INT NOT NULL DEFAULT 0,
    clean_sheets INT NOT NULL DEFAULT 0,

    -- Form and streak
    current_streak VARCHAR(10) DEFAULT 'N0',
    form_points_last5 INT NOT NULL DEFAULT 0,
    form_string VARCHAR(5) DEFAULT '',

    -- Elo rating (default 1500 = average)
    elo_rating DOUBLE NOT NULL DEFAULT 1500.0,

    -- Timestamps
    last_updated TIMESTAMP,

    -- Unique constraint for season + team combination
    CONSTRAINT uk_season_team UNIQUE (season_id, team_id)
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_sts_season ON season_team_stats(season_id);
CREATE INDEX IF NOT EXISTS idx_sts_team ON season_team_stats(team_id);
CREATE INDEX IF NOT EXISTS idx_sts_elo ON season_team_stats(elo_rating DESC);
CREATE INDEX IF NOT EXISTS idx_sts_form ON season_team_stats(form_points_last5 DESC);
CREATE INDEX IF NOT EXISTS idx_sts_points ON season_team_stats(wins, draws);

-- Add foreign key constraint (optional, depending on your schema)
-- ALTER TABLE season_team_stats ADD CONSTRAINT fk_sts_team
--     FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE;

-- ═══════════════════════════════════════════════════════════════════════════════
-- Sample data for testing (optional)
-- ═══════════════════════════════════════════════════════════════════════════════

-- INSERT INTO season_team_stats (season_id, team_id, team_name, matches_played, wins, draws, losses,
--     goals_scored, goals_conceded, clean_sheets, current_streak, form_points_last5, form_string,
--     elo_rating, last_updated)
-- VALUES
--     ('2025-26', 1, 'Arsenal', 20, 14, 4, 2, 45, 18, 8, 'W3', 13, 'WWWDW', 1650.5, CURRENT_TIMESTAMP),
--     ('2025-26', 2, 'Liverpool', 20, 13, 5, 2, 42, 15, 10, 'W2', 12, 'WDWWW', 1635.2, CURRENT_TIMESTAMP),
--     ('2025-26', 3, 'Man City', 20, 12, 5, 3, 40, 20, 6, 'D1', 10, 'DWWWD', 1610.8, CURRENT_TIMESTAMP);

