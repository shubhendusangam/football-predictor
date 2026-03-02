-- V4: Standardize season format to YYYY-YY (e.g., "2025-26")
-- Converts any seasons with "/" format to "-" format

-- Update matches table
UPDATE matches SET season = REPLACE(season, '/', '-') WHERE season LIKE '%/%';

-- Update league_standing table (if exists)
UPDATE league_standing SET season = REPLACE(season, '/', '-') WHERE season LIKE '%/%';

-- Update season_team_stats table (if exists)
UPDATE season_team_stats SET season_id = REPLACE(season_id, '/', '-') WHERE season_id LIKE '%/%';

