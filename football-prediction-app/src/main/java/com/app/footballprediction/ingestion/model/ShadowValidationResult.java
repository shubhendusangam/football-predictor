package com.app.footballprediction.ingestion.model;

import com.app.common.ingestion.dto.InternalMatchDto;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of shadow validation comparing new pipeline output
 * against existing database data.
 */
@Data
@Builder
public class ShadowValidationResult {

    /** Number of exact matches between new and existing data */
    private int exactMatches;

    /** Number of minor differences (acceptable variations) */
    private int minorDifferences;

    /** Critical differences that would affect business logic */
    @Builder.Default
    private List<ShadowDifference> criticalDifferences = new ArrayList<>();

    /** New matches not found in existing database */
    @Builder.Default
    private List<InternalMatchDto> newMatches = new ArrayList<>();

    /** Minor difference details */
    @Builder.Default
    private List<String> minorDiffDetails = new ArrayList<>();

    /**
     * Check if any critical differences were found.
     */
    public boolean hasCriticalDifferences() {
        return criticalDifferences != null && !criticalDifferences.isEmpty();
    }

    /**
     * Get the match rate (exact matches / total compared).
     */
    public double getMatchRate() {
        int total = exactMatches + minorDifferences +
                   (criticalDifferences != null ? criticalDifferences.size() : 0);
        if (total == 0) return 1.0;
        return (double) exactMatches / total;
    }

    /**
     * Check if validation passed (no critical differences).
     */
    public boolean passed() {
        return !hasCriticalDifferences();
    }

    /**
     * Get a summary string.
     */
    public String getSummary() {
        return String.format(
            "Exact: %d, Minor diffs: %d, Critical: %d, New: %d (Match rate: %.1f%%)",
            exactMatches,
            minorDifferences,
            criticalDifferences != null ? criticalDifferences.size() : 0,
            newMatches != null ? newMatches.size() : 0,
            getMatchRate() * 100
        );
    }

    /**
     * Record of a difference between expected and actual data.
     */
    @Data
    @Builder
    public static class ShadowDifference {
        private String matchBusinessKey;
        private String field;
        private String expectedValue;
        private String actualValue;
        private DifferenceType type;
        private String reason;

        /**
         * Types of differences.
         */
        public enum DifferenceType {
            /** Score mismatch (critical) */
            SCORE_MISMATCH,

            /** Result mismatch (critical) */
            RESULT_MISMATCH,

            /** Date mismatch (critical) */
            DATE_MISMATCH,

            /** Team name mismatch (may be normalization issue) */
            TEAM_NAME_MISMATCH,

            /** Statistics mismatch (usually minor) */
            STATS_MISMATCH,

            /** Other field mismatch */
            OTHER
        }

        /**
         * Check if this is a critical difference.
         */
        public boolean isCritical() {
            return type == DifferenceType.SCORE_MISMATCH ||
                   type == DifferenceType.RESULT_MISMATCH ||
                   type == DifferenceType.DATE_MISMATCH;
        }
    }

    /**
     * Builder helper for collecting validation results.
     */
    public static class Collector {
        private int exactMatches = 0;
        private int minorDifferences = 0;
        private final List<ShadowDifference> criticalDifferences = new ArrayList<>();
        private final List<InternalMatchDto> newMatches = new ArrayList<>();
        private final List<String> minorDiffDetails = new ArrayList<>();

        public void recordExactMatch() {
            exactMatches++;
        }

        public void recordMinorDiff(String detail) {
            minorDifferences++;
            minorDiffDetails.add(detail);
        }

        public void recordCriticalDiff(ShadowDifference diff) {
            criticalDifferences.add(diff);
        }

        public void recordNewMatch(InternalMatchDto match) {
            newMatches.add(match);
        }

        public ShadowValidationResult build() {
            return ShadowValidationResult.builder()
                .exactMatches(exactMatches)
                .minorDifferences(minorDifferences)
                .criticalDifferences(criticalDifferences)
                .newMatches(newMatches)
                .minorDiffDetails(minorDiffDetails)
                .build();
        }
    }
}

