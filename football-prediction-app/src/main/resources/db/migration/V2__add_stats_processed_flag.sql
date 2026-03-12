-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Add stats_processed flag to matches table
-- Database: H2
-- Date: 2026-02-23
-- Purpose: Idempotency protection for match stats processing
-- ═══════════════════════════════════════════════════════════════════════════════

-- Add stats_processed column to matches table
ALTER TABLE matches ADD COLUMN IF NOT EXISTS stats_processed BOOLEAN DEFAULT FALSE;

-- Create index for quickly finding unprocessed matches
CREATE INDEX IF NOT EXISTS idx_matches_stats_processed ON matches(stats_processed);

-- Add version column to season_team_stats for optimistic locking
ALTER TABLE season_team_stats ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;
