-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Create player_availability table
-- Database: H2
-- Date: 2026-03-19
-- Description: Tracks player injuries, suspensions, and fitness doubts to
--   compute squad strength features (Phase 10) for the prediction pipeline.
-- ═══════════════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS player_availability (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_name       VARCHAR(100) NOT NULL,
    player_name     VARCHAR(150) NOT NULL,
    position        VARCHAR(10),
    status          VARCHAR(20)  NOT NULL,
    reason          VARCHAR(500),
    expected_return DATE,
    importance_rating INT DEFAULT 5,
    is_key_star     BOOLEAN DEFAULT FALSE,
    avg_goals_per_game   DOUBLE DEFAULT 0.0,
    avg_assists_per_game DOUBLE DEFAULT 0.0,
    suspension_matches_remaining INT DEFAULT 0,
    report_date     DATE,
    season          VARCHAR(10)
);

-- ── Indexes ─────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_pa_team_season_status
    ON player_availability (team_name, season, status);

CREATE INDEX IF NOT EXISTS idx_pa_team_name
    ON player_availability (team_name);

