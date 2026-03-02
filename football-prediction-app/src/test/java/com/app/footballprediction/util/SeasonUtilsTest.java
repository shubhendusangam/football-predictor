package com.app.footballprediction.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SeasonUtils utility class.
 */
class SeasonUtilsTest {

    @Nested
    @DisplayName("getCurrentSeason Tests")
    class GetCurrentSeasonTests {

        @Test
        @DisplayName("Should return 2025-26 for February 2026")
        void getCurrentSeason_inFebruary2026_returns2025_26() {
            LocalDate feb2026 = LocalDate.of(2026, 2, 28);
            String season = SeasonUtils.getCurrentSeason(feb2026);
            assertThat(season).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should return 2025-26 for September 2025")
        void getCurrentSeason_inSeptember2025_returns2025_26() {
            LocalDate sep2025 = LocalDate.of(2025, 9, 1);
            String season = SeasonUtils.getCurrentSeason(sep2025);
            assertThat(season).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should return 2024-25 for July 2025")
        void getCurrentSeason_inJuly2025_returns2024_25() {
            LocalDate jul2025 = LocalDate.of(2025, 7, 15);
            String season = SeasonUtils.getCurrentSeason(jul2025);
            assertThat(season).isEqualTo("2024-25");
        }

        @Test
        @DisplayName("Should return 2025-26 for August 2025")
        void getCurrentSeason_inAugust2025_returns2025_26() {
            LocalDate aug2025 = LocalDate.of(2025, 8, 1);
            String season = SeasonUtils.getCurrentSeason(aug2025);
            assertThat(season).isEqualTo("2025-26");
        }
    }

    @Nested
    @DisplayName("normalizeSeason Tests")
    class NormalizeSeasonTests {

        @Test
        @DisplayName("Should convert slash format to dash format")
        void normalizeSeason_withSlash_convertsToDash() {
            assertThat(SeasonUtils.normalizeSeason("2025/26")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should keep dash format unchanged")
        void normalizeSeason_withDash_unchanged() {
            assertThat(SeasonUtils.normalizeSeason("2025-26")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle short year format with slash")
        void normalizeSeason_shortYearWithSlash_normalizes() {
            assertThat(SeasonUtils.normalizeSeason("25/26")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle single year")
        void normalizeSeason_singleYear_addsNextYear() {
            assertThat(SeasonUtils.normalizeSeason("2025")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle short single year")
        void normalizeSeason_shortSingleYear_addsNextYear() {
            assertThat(SeasonUtils.normalizeSeason("25")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle null input")
        void normalizeSeason_null_returnsCurrentSeason() {
            String result = SeasonUtils.normalizeSeason(null);
            assertThat(result).matches("\\d{4}-\\d{2}");
        }

        @Test
        @DisplayName("Should handle empty input")
        void normalizeSeason_empty_returnsCurrentSeason() {
            String result = SeasonUtils.normalizeSeason("");
            assertThat(result).matches("\\d{4}-\\d{2}");
        }
    }

    @Nested
    @DisplayName("formatSeason Tests")
    class FormatSeasonTests {

        @Test
        @DisplayName("Should format season correctly")
        void formatSeason_validYears_formatsCorrectly() {
            assertThat(SeasonUtils.formatSeason(2025, 2026)).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle century boundary")
        void formatSeason_centuryBoundary_formatsCorrectly() {
            assertThat(SeasonUtils.formatSeason(2099, 2100)).isEqualTo("2099-00");
        }
    }

    @Nested
    @DisplayName("extractSeasonFromStartDate Tests")
    class ExtractSeasonFromStartDateTests {

        @Test
        @DisplayName("Should extract season from start date")
        void extractSeasonFromStartDate_validDate_extractsCorrectly() {
            assertThat(SeasonUtils.extractSeasonFromStartDate("2025-08-15")).isEqualTo("2025-26");
        }

        @Test
        @DisplayName("Should handle null start date")
        void extractSeasonFromStartDate_null_returnsCurrentSeason() {
            String result = SeasonUtils.extractSeasonFromStartDate(null);
            assertThat(result).matches("\\d{4}-\\d{2}");
        }
    }

    @Nested
    @DisplayName("isValidSeason Tests")
    class IsValidSeasonTests {

        @Test
        @DisplayName("Should return true for valid season")
        void isValidSeason_validFormat_returnsTrue() {
            assertThat(SeasonUtils.isValidSeason("2025-26")).isTrue();
        }

        @Test
        @DisplayName("Should return false for slash format")
        void isValidSeason_slashFormat_returnsFalse() {
            assertThat(SeasonUtils.isValidSeason("2025/26")).isFalse();
        }

        @Test
        @DisplayName("Should return false for null")
        void isValidSeason_null_returnsFalse() {
            assertThat(SeasonUtils.isValidSeason(null)).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty")
        void isValidSeason_empty_returnsFalse() {
            assertThat(SeasonUtils.isValidSeason("")).isFalse();
        }
    }

    @Nested
    @DisplayName("getPreviousSeason Tests")
    class GetPreviousSeasonTests {

        @Test
        @DisplayName("Should get previous season correctly")
        void getPreviousSeason_validSeason_returnsPrevious() {
            assertThat(SeasonUtils.getPreviousSeason("2025-26")).isEqualTo("2024-25");
        }

        @Test
        @DisplayName("Should handle slash format input")
        void getPreviousSeason_slashFormat_normalizes() {
            assertThat(SeasonUtils.getPreviousSeason("2025/26")).isEqualTo("2024-25");
        }
    }

    @Nested
    @DisplayName("getNextSeason Tests")
    class GetNextSeasonTests {

        @Test
        @DisplayName("Should get next season correctly")
        void getNextSeason_validSeason_returnsNext() {
            assertThat(SeasonUtils.getNextSeason("2025-26")).isEqualTo("2026-27");
        }
    }
}

