-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Create prediction evaluation and model tracking tables
-- Database: H2
-- Date: 2026-03-08
-- Description: Tables for post-match recalculation engine
--   - prediction_evaluations: Stores detailed comparison of predictions vs results
--   - model_accuracy: Aggregated accuracy metrics (global, per-league, per-team)
--   - model_training_history: Audit trail of all model retraining events
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── prediction_evaluations ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS prediction_evaluations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id BIGINT NOT NULL,

    -- Predicted scores
    predicted_home_goals INT,
    predicted_away_goals INT,

    -- Actual scores
    actual_home_goals INT,
    actual_away_goals INT,

    -- Winner prediction
    predicted_winner VARCHAR(10),
    actual_winner VARCHAR(10),

    -- Error metrics
    goal_difference_error INT,
    winner_correct BOOLEAN NOT NULL DEFAULT FALSE,
    score_exact BOOLEAN NOT NULL DEFAULT FALSE,
    card_prediction_error INT,
    corner_prediction_error INT,

    -- Match metadata
    home_team VARCHAR(255),
    away_team VARCHAR(255),
    season VARCHAR(20),
    prediction_confidence DOUBLE,

    -- Timestamp
    evaluation_time TIMESTAMP NOT NULL,

    -- Unique constraint: one evaluation per match
    CONSTRAINT uq_eval_match_id UNIQUE (match_id)
);

-- Indexes for prediction_evaluations
CREATE INDEX IF NOT EXISTS idx_eval_match ON prediction_evaluations (match_id);
CREATE INDEX IF NOT EXISTS idx_eval_time ON prediction_evaluations (evaluation_time);
CREATE INDEX IF NOT EXISTS idx_eval_winner_correct ON prediction_evaluations (winner_correct);

-- ── model_accuracy ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS model_accuracy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Scope: GLOBAL, LEAGUE, TEAM
    scope VARCHAR(20) NOT NULL,
    scope_key VARCHAR(255),

    -- Counts
    total_predictions BIGINT NOT NULL DEFAULT 0,
    correct_winner_predictions BIGINT NOT NULL DEFAULT 0,
    exact_score_predictions BIGINT NOT NULL DEFAULT 0,

    -- Accuracy rates (0.0 to 1.0)
    winner_accuracy DOUBLE NOT NULL DEFAULT 0.0,
    score_accuracy DOUBLE NOT NULL DEFAULT 0.0,

    -- Error averages
    goal_error_average DOUBLE NOT NULL DEFAULT 0.0,
    card_error_average DOUBLE DEFAULT 0.0,
    corner_error_average DOUBLE DEFAULT 0.0,

    -- Metadata
    season VARCHAR(20),
    calculated_at TIMESTAMP NOT NULL
);

-- Indexes for model_accuracy
CREATE INDEX IF NOT EXISTS idx_accuracy_scope ON model_accuracy (scope);
CREATE INDEX IF NOT EXISTS idx_accuracy_scope_key ON model_accuracy (scope_key);
CREATE INDEX IF NOT EXISTS idx_accuracy_calculated ON model_accuracy (calculated_at);

-- ── model_training_history ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS model_training_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    -- Training event details
    training_time TIMESTAMP NOT NULL,
    trigger_reason VARCHAR(50) NOT NULL,

    -- Previous metrics (before retraining)
    previous_winner_accuracy DOUBLE,
    previous_goal_error DOUBLE,
    previous_card_error DOUBLE,
    previous_corner_error DOUBLE,

    -- New metrics (after retraining)
    new_winner_accuracy DOUBLE,
    new_goal_error DOUBLE,
    new_card_error DOUBLE,
    new_corner_error DOUBLE,

    -- Training details
    matches_used INT,
    new_evaluations INT,
    weight_adjustments TEXT,
    training_duration_ms BIGINT,

    -- Status
    success BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    model_version VARCHAR(100)
);

-- Indexes for model_training_history
CREATE INDEX IF NOT EXISTS idx_training_time ON model_training_history (training_time);
CREATE INDEX IF NOT EXISTS idx_training_trigger ON model_training_history (trigger_reason);

