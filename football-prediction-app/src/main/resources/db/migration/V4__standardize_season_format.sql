-- V4: Standardize season format to YYYY-YY (e.g., "2025-26")
-- Converts any seasons with "/" format to "-" format
-- Uses H2 ALIAS for safe execution (tables may not exist on fresh databases)

-- Create a temporary helper for safe UPDATE execution
CREATE ALIAS IF NOT EXISTS SAFE_EXECUTE AS $$
void safeExecute(java.sql.Connection conn, String sql) throws Exception {
    try { conn.createStatement().execute(sql); } catch (Exception e) { /* table may not exist yet */ }
}
$$;

-- Update matches table
CALL SAFE_EXECUTE('UPDATE matches SET season = REPLACE(season, ''/'', ''-'') WHERE season LIKE ''%/%''');

-- Update league_standings table (note: plural)
CALL SAFE_EXECUTE('UPDATE league_standings SET season = REPLACE(season, ''/'', ''-'') WHERE season LIKE ''%/%''');

-- Update season_team_stats table
CALL SAFE_EXECUTE('UPDATE season_team_stats SET season_id = REPLACE(season_id, ''/'', ''-'') WHERE season_id LIKE ''%/%''');

-- Clean up helper
DROP ALIAS IF EXISTS SAFE_EXECUTE;
