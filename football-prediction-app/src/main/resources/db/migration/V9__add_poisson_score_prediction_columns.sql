-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Add Poisson score prediction columns
-- Database: H2
-- Date: 2026-03-19
-- Description: Adds columns for Dixon-Coles Poisson score prediction tracking
--
--   prediction_evaluations: 3 new columns
--     - poisson_predicted_score: The predicted scoreline (e.g. "2-1")
--     - poisson_score_exact:     Whether the Poisson model predicted the exact score
--     - poisson_goal_error:      MAE of Poisson lambdas vs actual goals
--
--   model_accuracy: 2 new columns
--     - poisson_score_accuracy:       Exact score accuracy rate (0.0-1.0)
--     - poisson_goal_error_average:   Average Poisson goal error across evaluations
-- ═══════════════════════════════════════════════════════════════════════════════

-- ── prediction_evaluations: Poisson score tracking columns ────────────
ALTER TABLE prediction_evaluations ADD COLUMN IF NOT EXISTS poisson_predicted_score VARCHAR(10);

ALTER TABLE prediction_evaluations ADD COLUMN IF NOT EXISTS poisson_score_exact BOOLEAN DEFAULT FALSE;

ALTER TABLE prediction_evaluations ADD COLUMN IF NOT EXISTS poisson_goal_error DOUBLE;

-- ── model_accuracy: Poisson accuracy metrics ──────────────────────────
ALTER TABLE model_accuracy ADD COLUMN IF NOT EXISTS poisson_score_accuracy DOUBLE DEFAULT 0.0;

ALTER TABLE model_accuracy ADD COLUMN IF NOT EXISTS poisson_goal_error_average DOUBLE DEFAULT 0.0;

