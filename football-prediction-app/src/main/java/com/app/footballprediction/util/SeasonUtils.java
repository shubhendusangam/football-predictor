package com.app.footballprediction.util;

import com.app.common.util.SeasonHelper;

import java.time.LocalDate;

/**
 * Thin delegate to {@link SeasonHelper} in the common module.
 *
 * <p>All season logic now lives in {@code SeasonHelper}. This class is retained
 * only for backward compatibility within the app module and will be removed in
 * a future cleanup pass. New code should import {@code SeasonHelper} directly.</p>
 *
 * @deprecated Use {@link SeasonHelper} instead.
 */
@Deprecated(forRemoval = true)
public final class SeasonUtils {

    public static final String SEASON_FORMAT_PATTERN = SeasonHelper.SEASON_FORMAT_PATTERN;

    private SeasonUtils() {}

    public static String getCurrentSeason() {
        return SeasonHelper.currentSeason();
    }

    public static String getCurrentSeason(LocalDate date) {
        return SeasonHelper.deriveSeason(date);
    }

    public static String formatSeason(int startYear, int endYear) {
        return SeasonHelper.formatSeason(startYear, endYear);
    }

    public static String normalizeSeason(String season) {
        return SeasonHelper.normalizeSeason(season);
    }

    public static String extractSeasonFromDate(String dateString) {
        return SeasonHelper.extractSeasonFromDate(dateString);
    }

    public static String extractSeasonFromStartDate(String startDate) {
        return SeasonHelper.extractSeasonFromStartDate(startDate);
    }

    public static boolean isValidSeason(String season) {
        return SeasonHelper.isValidSeason(season);
    }

    public static String getPreviousSeason(String season) {
        return SeasonHelper.getPreviousSeason(season);
    }

    public static String getNextSeason(String season) {
        return SeasonHelper.getNextSeason(season);
    }

    public static String formatForDisplay(String season) {
        return SeasonHelper.formatForDisplay(season);
    }
}
