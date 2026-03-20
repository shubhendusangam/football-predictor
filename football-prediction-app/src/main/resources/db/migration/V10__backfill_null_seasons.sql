-- ═══════════════════════════════════════════════════════════════════════════════
-- Migration: Backfill NULL season values from match_date
-- Database: H2
-- Date: 2026-03-19
-- Description: Matches loaded before season extraction was added, or inserted
--   via older API sync code, may have season = NULL. This migration derives the
--   season from match_date using the Aug–Jul football calendar convention:
--     Aug–Dec → season started that year   (Oct 2025 → '2025-26')
--     Jan–Jul → season started prior year  (Mar 2026 → '2025-26')
-- ═══════════════════════════════════════════════════════════════════════════════

UPDATE matches
SET season = CONCAT(
    CAST(
        CASE
            WHEN EXTRACT(MONTH FROM match_date) >= 8
                THEN EXTRACT(YEAR FROM match_date)
            ELSE EXTRACT(YEAR FROM match_date) - 1
        END AS VARCHAR
    ),
    '-',
    LPAD(
        CAST(
            CASE
                WHEN EXTRACT(MONTH FROM match_date) >= 8
                    THEN (EXTRACT(YEAR FROM match_date) + 1) % 100
                ELSE EXTRACT(YEAR FROM match_date) % 100
            END AS VARCHAR
        ),
        2,
        '0'
    )
)
WHERE season IS NULL
  AND match_date IS NOT NULL;

