-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Add logo tracking fields for efficient logo management
-- Database: H2
-- Date: 2026-02-23
-- Description: Adds fields to track when logos were last updated and for which
--              season, preventing redundant logo seeding on every startup.
-- ═══════════════════════════════════════════════════════════════════════════════

-- Add logo tracking columns to teams table
ALTER TABLE teams ADD COLUMN IF NOT EXISTS logo_last_updated TIMESTAMP;
ALTER TABLE teams ADD COLUMN IF NOT EXISTS logo_seeded_season VARCHAR(20);

-- Add logo seeding tracking to system_settings table
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS logo_seeding_season VARCHAR(20);
ALTER TABLE system_settings ADD COLUMN IF NOT EXISTS logo_seeding_timestamp TIMESTAMP;

-- Create index for efficient season-based logo queries
CREATE INDEX IF NOT EXISTS idx_teams_logo_season ON teams(logo_seeded_season);
