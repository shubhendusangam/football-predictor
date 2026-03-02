package com.app.footballprediction.util;

import java.time.LocalDate;

/**
 * Utility class for standardizing season format across the system.
 *
 * The standard format is: YYYY-YY (e.g., "2025-26", "2026-27")
 * This format is used consistently for:
 * - Database storage
 * - API responses
 * - Frontend display
 * - Data from external APIs (normalized on ingestion)
 */
public final class SeasonUtils {

    /**
     * Standard season format: YYYY-YY (e.g., "2025-26")
     */
    public static final String SEASON_FORMAT_PATTERN = "\\d{4}-\\d{2}";

    private SeasonUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the current season string in standard format (YYYY-YY).
     *
     * Football seasons typically run from August to May:
     * - August 2025 to May 2026 = "2025-26"
     * - If current date is before August, we're in the second half of the previous season
     *
     * @return Current season string (e.g., "2025-26")
     */
    public static String getCurrentSeason() {
        return getCurrentSeason(LocalDate.now());
    }

    /**
     * Get the season string for a given date.
     *
     * @param date The date to determine the season for
     * @return Season string (e.g., "2025-26")
     */
    public static String getCurrentSeason(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();

        // Season starts in August
        if (month < 8) {
            // Jan-Jul: We're in the second half of the season (e.g., Feb 2026 = 2025-26)
            return formatSeason(year - 1, year);
        } else {
            // Aug-Dec: We're in the first half of the season (e.g., Sep 2025 = 2025-26)
            return formatSeason(year, year + 1);
        }
    }

    /**
     * Format a season string from start and end years.
     *
     * @param startYear The year the season starts (e.g., 2025)
     * @param endYear The year the season ends (e.g., 2026)
     * @return Formatted season string (e.g., "2025-26")
     */
    public static String formatSeason(int startYear, int endYear) {
        return String.format("%d-%02d", startYear, endYear % 100);
    }

    /**
     * Normalize a season string to standard format (YYYY-YY).
     *
     * Handles various input formats:
     * - "2025/26" -> "2025-26"
     * - "2025-26" -> "2025-26" (already correct)
     * - "25/26" -> "2025-26"
     * - "25-26" -> "2025-26"
     * - "2025" -> "2025-26" (assumes full year is start)
     *
     * @param season The season string to normalize
     * @return Normalized season string in YYYY-YY format
     */
    public static String normalizeSeason(String season) {
        if (season == null || season.trim().isEmpty()) {
            return getCurrentSeason();
        }

        String trimmed = season.trim();

        // Already in standard format
        if (trimmed.matches(SEASON_FORMAT_PATTERN)) {
            return trimmed;
        }

        // Handle slash format: "2025/26" -> "2025-26"
        if (trimmed.contains("/")) {
            String[] parts = trimmed.split("/");
            if (parts.length == 2) {
                return normalizeSeasonParts(parts[0], parts[1]);
            }
        }

        // Handle dash format with wrong year format: "25-26" -> "2025-26"
        if (trimmed.contains("-") && !trimmed.matches(SEASON_FORMAT_PATTERN)) {
            String[] parts = trimmed.split("-");
            if (parts.length == 2) {
                return normalizeSeasonParts(parts[0], parts[1]);
            }
        }

        // Handle single year: "2025" -> "2025-26"
        if (trimmed.matches("\\d{4}")) {
            int year = Integer.parseInt(trimmed);
            return formatSeason(year, year + 1);
        }

        // Handle short single year: "25" -> "2025-26"
        if (trimmed.matches("\\d{2}")) {
            int shortYear = Integer.parseInt(trimmed);
            int year = 2000 + shortYear;
            return formatSeason(year, year + 1);
        }

        // Return as-is if we can't parse (log warning in calling code)
        return trimmed;
    }

    /**
     * Normalize season parts to standard format.
     */
    private static String normalizeSeasonParts(String startPart, String endPart) {
        int startYear;
        int endYear;

        // Parse start year
        if (startPart.length() == 4) {
            startYear = Integer.parseInt(startPart);
        } else if (startPart.length() == 2) {
            startYear = 2000 + Integer.parseInt(startPart);
        } else {
            return startPart + "-" + endPart; // Can't parse, return with dash
        }

        // Parse end year
        if (endPart.length() == 2) {
            endYear = (startYear / 100) * 100 + Integer.parseInt(endPart);
        } else if (endPart.length() == 4) {
            endYear = Integer.parseInt(endPart);
        } else {
            endYear = startYear + 1;
        }

        return formatSeason(startYear, endYear);
    }

    /**
     * Extract season from API date string.
     *
     * @param dateString Date in format "YYYY-MM-DD" (e.g., "2025-08-15")
     * @return Season string (e.g., "2025-26")
     */
    public static String extractSeasonFromDate(String dateString) {
        if (dateString == null || dateString.length() < 10) {
            return getCurrentSeason();
        }

        try {
            LocalDate date = LocalDate.parse(dateString.substring(0, 10));
            return getCurrentSeason(date);
        } catch (Exception e) {
            return getCurrentSeason();
        }
    }

    /**
     * Extract season from API season start date.
     * For API responses where the season start date is given.
     *
     * @param startDate Season start date (e.g., "2025-08-15")
     * @return Season string (e.g., "2025-26")
     */
    public static String extractSeasonFromStartDate(String startDate) {
        if (startDate == null || startDate.length() < 4) {
            return getCurrentSeason();
        }

        try {
            int startYear = Integer.parseInt(startDate.substring(0, 4));
            return formatSeason(startYear, startYear + 1);
        } catch (NumberFormatException e) {
            return getCurrentSeason();
        }
    }

    /**
     * Check if a season string is in valid format.
     *
     * @param season The season string to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidSeason(String season) {
        if (season == null || season.trim().isEmpty()) {
            return false;
        }
        return season.matches(SEASON_FORMAT_PATTERN);
    }

    /**
     * Get the previous season.
     *
     * @param season Current season (e.g., "2025-26")
     * @return Previous season (e.g., "2024-25")
     */
    public static String getPreviousSeason(String season) {
        if (!isValidSeason(season)) {
            season = normalizeSeason(season);
        }

        try {
            int startYear = Integer.parseInt(season.substring(0, 4));
            return formatSeason(startYear - 1, startYear);
        } catch (Exception e) {
            return season;
        }
    }

    /**
     * Get the next season.
     *
     * @param season Current season (e.g., "2025-26")
     * @return Next season (e.g., "2026-27")
     */
    public static String getNextSeason(String season) {
        if (!isValidSeason(season)) {
            season = normalizeSeason(season);
        }

        try {
            int startYear = Integer.parseInt(season.substring(0, 4));
            return formatSeason(startYear + 1, startYear + 2);
        } catch (Exception e) {
            return season;
        }
    }

    /**
     * Format season for display (same as standard format for now).
     * Can be customized for different display formats if needed.
     *
     * @param season Season in standard format
     * @return Display-friendly season string
     */
    public static String formatForDisplay(String season) {
        return normalizeSeason(season);
    }
}

