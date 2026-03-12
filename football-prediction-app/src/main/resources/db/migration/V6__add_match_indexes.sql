-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Add performance indexes to matches table
-- Database: H2
-- Date: 2026-03-12
-- Description: Adds indexes on frequently queried column patterns to eliminate
--   full table scans in:
--   - CSV ingestion duplicate checks (match_date, home_team, away_team)
--   - Season-filtered form queries (season, home_team/away_team, match_date)
--   - Team history queries (home_team/away_team, match_date)
-- ═══════════════════════════════════════════════════════════════════════════════

-- Composite index for dedup checks: existsByMatchDateAndHomeTeamAndAwayTeam,
-- findByMatchDateAndHomeTeamAndAwayTeam
CREATE INDEX IF NOT EXISTS idx_match_date_teams
    ON matches (match_date, home_team, away_team);

-- Composite indexes for season-filtered queries:
-- findByTeamAndSeasonBeforeDate, findHomeMatchesByTeamSeasonBeforeDateLimited, etc.
CREATE INDEX IF NOT EXISTS idx_match_season_home
    ON matches (season, home_team, match_date);

CREATE INDEX IF NOT EXISTS idx_match_season_away
    ON matches (season, away_team, match_date);

-- Index for season + result queries:
-- findBySeasonOrderByMatchDateDesc, findAllDistinctTeamNamesBySeason
CREATE INDEX IF NOT EXISTS idx_match_season_ftr
    ON matches (season, full_time_result);

-- Indexes for team history queries:
-- findByTeamBeforeDate, findHomeMatchesByTeamBeforeDate, findAwayMatchesByTeamBeforeDate
CREATE INDEX IF NOT EXISTS idx_match_home_date
    ON matches (home_team, match_date);

CREATE INDEX IF NOT EXISTS idx_match_away_date
    ON matches (away_team, match_date);
