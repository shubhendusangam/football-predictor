-- ============================================================================
-- V7: Create sync_status table for persisted sync audit trail
-- Tracks every sync operation (fixtures, results, standings, full)
-- with success/failure, record counts, and timing information.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sync_status (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type       VARCHAR(30)  NOT NULL,
    competition     VARCHAR(10),
    started_at      TIMESTAMP    NOT NULL,
    finished_at     TIMESTAMP,
    duration_ms     BIGINT,
    records_fetched  INT DEFAULT 0,
    records_inserted INT DEFAULT 0,
    records_updated  INT DEFAULT 0,
    records_skipped  INT DEFAULT 0,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,
    error_message   VARCHAR(2000),
    triggered_by    VARCHAR(100),
    retry_count     INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sync_status_type     ON sync_status(sync_type);
CREATE INDEX IF NOT EXISTS idx_sync_status_started  ON sync_status(started_at);

