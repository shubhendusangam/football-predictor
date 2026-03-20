-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration V1: Create ALL base tables
-- Database: H2
-- Date: 2026-02-23 (updated 2026-03-20)
--
-- Creates every table used by the application so that subsequent
-- ALTER TABLE / CREATE INDEX migrations (V2..V11) always find their
-- target tables.  Uses IF NOT EXISTS for idempotency.
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── matches ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS matches (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_date            DATE,
    home_team             VARCHAR(255),
    away_team             VARCHAR(255),
    season                VARCHAR(20),
    referee               VARCHAR(255),
    kickoff_time          VARCHAR(20),

    full_time_home_goals  INT,
    full_time_away_goals  INT,
    full_time_result      VARCHAR(5),

    half_time_home_goals  INT,
    half_time_away_goals  INT,
    half_time_result      VARCHAR(5),

    stats_processed       BOOLEAN DEFAULT FALSE,

    home_shots            INT,
    away_shots            INT,
    home_shots_on_target  INT,
    away_shots_on_target  INT,
    home_corners          INT,
    away_corners          INT,
    home_yellow_cards     INT,
    away_yellow_cards     INT,
    home_red_cards        INT,
    away_red_cards        INT,
    home_fouls            INT,
    away_fouls            INT,

    -- Betting odds
    b365h DOUBLE, b365d DOUBLE, b365a DOUBLE,
    bwh   DOUBLE, bwd   DOUBLE, bwa   DOUBLE,
    iwh   DOUBLE, iwd   DOUBLE, iwa   DOUBLE,
    psh   DOUBLE, psd   DOUBLE, psa   DOUBLE,
    whh   DOUBLE, whd   DOUBLE, wha   DOUBLE
);

-- ── teams ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS teams (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(255) NOT NULL UNIQUE,
    logo_url            VARCHAR(500),
    short_name          VARCHAR(50),
    primary_color       VARCHAR(20),
    logo_last_updated   TIMESTAMP,
    logo_seeded_season  VARCHAR(20)
);

-- ── leagues ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS leagues (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(10) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    country_code    VARCHAR(10),
    country_name    VARCHAR(255),
    logo_url        VARCHAR(500),
    enabled         BOOLEAN DEFAULT TRUE,
    display_order   INT DEFAULT 100,
    current_season  VARCHAR(20)
);

-- ── league_standings ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS league_standings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    league_id       BIGINT NOT NULL,
    season          VARCHAR(20) NOT NULL,
    team_id         BIGINT,
    team_name       VARCHAR(255) NOT NULL,
    position        INT,
    played          INT DEFAULT 0,
    won             INT DEFAULT 0,
    drawn           INT DEFAULT 0,
    lost            INT DEFAULT 0,
    goals_for       INT DEFAULT 0,
    goals_against   INT DEFAULT 0,
    goal_difference INT DEFAULT 0,
    points          INT DEFAULT 0,
    form            VARCHAR(20),
    logo_url        VARCHAR(500)
);

-- ── predictions ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS predictions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id             BIGINT NOT NULL,
    team_id              BIGINT,
    team_name            VARCHAR(255) NOT NULL,
    opponent_name        VARCHAR(255),
    is_home              BOOLEAN NOT NULL,
    season               VARCHAR(20),
    match_date           DATE,
    predicted_result     VARCHAR(10) NOT NULL,
    actual_result        VARCHAR(10),
    predicted_home_goals INT,
    predicted_away_goals INT,
    actual_home_goals    INT,
    actual_away_goals    INT,
    confidence           DOUBLE NOT NULL,
    prob_home_win        DOUBLE,
    prob_draw            DOUBLE,
    prob_away_win        DOUBLE,
    prediction_date      TIMESTAMP,
    evaluated            BOOLEAN DEFAULT FALSE
);

-- ── system_settings ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS system_settings (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    prediction_engine_enabled   BOOLEAN DEFAULT TRUE,
    auto_retrain_enabled        BOOLEAN DEFAULT TRUE,
    auto_fetch_enabled          BOOLEAN DEFAULT TRUE,
    min_confidence_threshold    INT DEFAULT 60,
    form_window_size            INT DEFAULT 5,
    default_league              VARCHAR(10) DEFAULT 'PL',
    maintenance_mode            BOOLEAN DEFAULT FALSE,
    cache_ttl_minutes           INT DEFAULT 60,
    last_model_training         TIMESTAMP,
    last_data_fetch             TIMESTAMP,
    model_accuracy              DOUBLE,
    logo_seeding_season         VARCHAR(20),
    logo_seeding_timestamp      TIMESTAMP
);

-- ── admin_audit_log ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    username            VARCHAR(255) NOT NULL,
    action_type         VARCHAR(50) NOT NULL,
    action_description  VARCHAR(1000),
    target_entity       VARCHAR(255),
    target_id           VARCHAR(255),
    previous_value      VARCHAR(2000),
    new_value           VARCHAR(2000),
    ip_address          VARCHAR(50),
    user_agent          VARCHAR(500),
    success             BOOLEAN DEFAULT TRUE,
    error_message       VARCHAR(1000),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ── season_team_stats ───────────────────────────────────────────────────
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

