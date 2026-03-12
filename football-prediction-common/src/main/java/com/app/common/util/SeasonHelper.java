package com.app.common.util;

import java.time.LocalDate;

/**
 * Single source of truth for season-string operations across all modules.
 *
 * <p>Football seasons run August → May. The standard format is {@code "YYYY-YY"}
 * (e.g., {@code "2025-26"}). This class centralizes every season derivation,
 * normalization, and comparison method so that it is defined once and reused
 * everywhere.</p>
 *
 * <p><strong>Replaces:</strong> {@code SeasonUtils} (app module) and inline
 * {@code deriveSeason()} helpers formerly scattered across services.</p>
 */
public final class SeasonHelper {

    /** Regex pattern for the canonical season format: {@code YYYY-YY}. */
    public static final String SEASON_FORMAT_PATTERN = "\\d{4}-\\d{2}";

    private SeasonHelper() {
        // Utility class — prevent instantiation
    }

    // ── Core derivation ────────────────────────────────────────────────────

    /**
     * Derive the season string for a given date.
     *
     * <ul>
     *   <li>Aug–Dec → season started that year  (Oct 2025 → "2025-26")</li>
     *   <li>Jan–Jul → season started previous year (Mar 2026 → "2025-26")</li>
     * </ul>
     *
     * @param date the reference date
     * @return season in {@code "YYYY-YY"} format
     */
    public static String deriveSeason(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        int startYear = month >= 8 ? year : year - 1;
        return formatSeason(startYear, startYear + 1);
    }

    /** Convenience shorthand — current season based on today's date. */
    public static String currentSeason() {
        return deriveSeason(LocalDate.now());
    }

    // ── Formatting ─────────────────────────────────────────────────────────

    /**
     * Format a season string from start and end years.
     *
     * @param startYear e.g. 2025
     * @param endYear   e.g. 2026
     * @return {@code "2025-26"}
     */
    public static String formatSeason(int startYear, int endYear) {
        return String.format("%d-%02d", startYear, endYear % 100);
    }

    // ── Normalization ──────────────────────────────────────────────────────

    /**
     * Normalize any season string to the canonical format ({@code YYYY-YY}).
     *
     * Handles: {@code "2025/26"}, {@code "25-26"}, {@code "2025"},
     * {@code "25"}, and already-correct {@code "2025-26"}.
     *
     * @param season raw season string (nullable — falls back to current season)
     * @return normalized season string
     */
    public static String normalizeSeason(String season) {
        if (season == null || season.trim().isEmpty()) {
            return currentSeason();
        }

        String trimmed = season.trim();

        // Already canonical
        if (trimmed.matches(SEASON_FORMAT_PATTERN)) {
            return trimmed;
        }

        // Slash format: "2025/26"
        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/");
            if (parts.length == 2) {
                return normalizeSeasonParts(parts[0], parts[1]);
            }
        }

        // Non-canonical dash: "25-26"
        if (trimmed.contains("-")) {
            String[] parts = trimmed.split("-");
            if (parts.length == 2) {
                return normalizeSeasonParts(parts[0], parts[1]);
            }
        }

        // Full year only: "2025"
        if (trimmed.matches("\\d{4}")) {
            int year = Integer.parseInt(trimmed);
            return formatSeason(year, year + 1);
        }

        // Short year only: "25"
        if (trimmed.matches("\\d{2}")) {
            int year = 2000 + Integer.parseInt(trimmed);
            return formatSeason(year, year + 1);
        }

        return trimmed; // un-parsable — return as-is
    }

    // ── Extraction ─────────────────────────────────────────────────────────

    /**
     * Extract season from a date string (e.g. an API's {@code "2025-08-15"}).
     *
     * @param dateString date in {@code "YYYY-MM-DD"} (only first 10 chars used)
     * @return season in canonical format
     */
    public static String extractSeasonFromDate(String dateString) {
        if (dateString == null || dateString.length() < 10) {
            return currentSeason();
        }
        try {
            return deriveSeason(LocalDate.parse(dateString.substring(0, 10)));
        } catch (Exception e) {
            return currentSeason();
        }
    }

    /**
     * Extract season from an API season-start date (e.g. {@code "2025-08-15"}).
     * Uses the year portion directly as the start year.
     *
     * @param startDate season start date string
     * @return season in canonical format
     */
    public static String extractSeasonFromStartDate(String startDate) {
        if (startDate == null || startDate.length() < 4) {
            return currentSeason();
        }
        try {
            int startYear = Integer.parseInt(startDate.substring(0, 4));
            return formatSeason(startYear, startYear + 1);
        } catch (NumberFormatException e) {
            return currentSeason();
        }
    }

    // ── Validation ─────────────────────────────────────────────────────────

    /**
     * Check if a season string matches the canonical format.
     */
    public static boolean isValidSeason(String season) {
        return season != null && season.matches(SEASON_FORMAT_PATTERN);
    }

    // ── Navigation ─────────────────────────────────────────────────────────

    /**
     * Get the previous season. {@code "2025-26" → "2024-25"}.
     */
    public static String getPreviousSeason(String season) {
        String s = isValidSeason(season) ? season : normalizeSeason(season);
        try {
            int startYear = Integer.parseInt(s.substring(0, 4));
            return formatSeason(startYear - 1, startYear);
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * Get the next season. {@code "2025-26" → "2026-27"}.
     */
    public static String getNextSeason(String season) {
        String s = isValidSeason(season) ? season : normalizeSeason(season);
        try {
            int startYear = Integer.parseInt(s.substring(0, 4));
            return formatSeason(startYear + 1, startYear + 2);
        } catch (Exception e) {
            return s;
        }
    }

    /** Alias for display — same canonical format. */
    public static String formatForDisplay(String season) {
        return normalizeSeason(season);
    }

    // ── Private ────────────────────────────────────────────────────────────

    private static String normalizeSeasonParts(String startPart, String endPart) {
        int startYear;
        if (startPart.length() == 4) {
            startYear = Integer.parseInt(startPart);
        } else if (startPart.length() == 2) {
            startYear = 2000 + Integer.parseInt(startPart);
        } else {
            return startPart + "-" + endPart;
        }

        int endYear;
        if (endPart.length() == 2) {
            endYear = (startYear / 100) * 100 + Integer.parseInt(endPart);
        } else if (endPart.length() == 4) {
            endYear = Integer.parseInt(endPart);
        } else {
            endYear = startYear + 1;
        }

        return formatSeason(startYear, endYear);
    }
}
