package com.app.footballprediction.ingestion.model;

import com.app.common.model.Match;
import lombok.Builder;
import lombok.Data;

import java.util.*;

/**
 * Result of an upsert batch operation.
 * Tracks which matches were inserted, updated, or skipped.
 */
@Data
@Builder
public class UpsertResult {

    @Builder.Default
    private List<Match> insertedMatches = new ArrayList<>();

    @Builder.Default
    private List<Match> updatedMatches = new ArrayList<>();

    @Builder.Default
    private List<SkippedMatch> skippedMatches = new ArrayList<>();

    @Builder.Default
    private List<ErrorRecord> errors = new ArrayList<>();

    @Builder.Default
    private Set<String> affectedSeasons = new HashSet<>();

    @Builder.Default
    private Set<String> affectedTeams = new HashSet<>();

    /**
     * Get count of inserted matches.
     */
    public int getInserted() {
        return insertedMatches.size();
    }

    /**
     * Get count of updated matches.
     */
    public int getUpdated() {
        return updatedMatches.size();
    }

    /**
     * Get count of skipped matches.
     */
    public int getSkipped() {
        return skippedMatches.size();
    }

    /**
     * Check if any changes were made.
     */
    public boolean hasChanges() {
        return !insertedMatches.isEmpty() || !updatedMatches.isEmpty();
    }

    /**
     * Get all affected matches (inserted + updated).
     */
    public List<Match> getAffectedMatches() {
        List<Match> affected = new ArrayList<>();
        affected.addAll(insertedMatches);
        affected.addAll(updatedMatches);
        return affected;
    }

    /**
     * Get cache keys that should be invalidated.
     */
    public Set<String> getAffectedCacheKeys() {
        Set<String> keys = new HashSet<>(affectedSeasons);
        keys.add("all"); // Global cache key
        return keys;
    }

    /**
     * Record of a skipped match.
     */
    @Data
    @Builder
    public static class SkippedMatch {
        private String businessKey;
        private String reason;
    }

    /**
     * Record of an error during upsert.
     */
    @Data
    @Builder
    public static class ErrorRecord {
        private String businessKey;
        private String errorMessage;
        private String errorType;
    }

    /**
     * Builder for constructing UpsertResult incrementally.
     */
    public static class UpsertResultCollector {
        private final List<Match> insertedMatches = new ArrayList<>();
        private final List<Match> updatedMatches = new ArrayList<>();
        private final List<SkippedMatch> skippedMatches = new ArrayList<>();
        private final List<ErrorRecord> errors = new ArrayList<>();
        private final Set<String> affectedSeasons = new HashSet<>();
        private final Set<String> affectedTeams = new HashSet<>();

        public void recordInsert(Match match) {
            insertedMatches.add(match);
            trackAffected(match);
        }

        public void recordUpdate(Match match) {
            updatedMatches.add(match);
            trackAffected(match);
        }

        public void recordSkipped(String businessKey, String reason) {
            skippedMatches.add(SkippedMatch.builder()
                .businessKey(businessKey)
                .reason(reason)
                .build());
        }

        public void recordError(String businessKey, String errorMessage) {
            errors.add(ErrorRecord.builder()
                .businessKey(businessKey)
                .errorMessage(errorMessage)
                .build());
        }

        private void trackAffected(Match match) {
            if (match.getSeason() != null) {
                affectedSeasons.add(match.getSeason());
            }
            if (match.getHomeTeam() != null) {
                affectedTeams.add(match.getHomeTeam());
            }
            if (match.getAwayTeam() != null) {
                affectedTeams.add(match.getAwayTeam());
            }
        }

        public UpsertResult build() {
            return UpsertResult.builder()
                .insertedMatches(insertedMatches)
                .updatedMatches(updatedMatches)
                .skippedMatches(skippedMatches)
                .errors(errors)
                .affectedSeasons(affectedSeasons)
                .affectedTeams(affectedTeams)
                .build();
        }
    }
}

