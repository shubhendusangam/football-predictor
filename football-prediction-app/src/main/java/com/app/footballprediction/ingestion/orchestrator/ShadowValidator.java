package com.app.footballprediction.ingestion.orchestrator;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.ingestion.model.ShadowValidationResult;
import com.app.footballprediction.ingestion.model.ShadowValidationResult.ShadowDifference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates new ingestion pipeline output against existing database.
 * Runs in shadow mode - compares data without making any writes.
 *
 * <p>Shadow validation helps ensure:
 * <ul>
 *   <li>New pipeline produces identical results to legacy</li>
 *   <li>No data corruption during migration</li>
 *   <li>Safe rollout with confidence</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShadowValidator {

    private final MatchRepository matchRepository;

    /**
     * Validate new pipeline data against existing database.
     *
     * @param newPipelineData Data from new ingestion pipeline
     * @return Validation result with difference details
     */
    public ShadowValidationResult validate(List<InternalMatchDto> newPipelineData) {
        log.info("Starting shadow validation for {} matches", newPipelineData.size());

        ShadowValidationResult.Collector collector = new ShadowValidationResult.Collector();

        for (InternalMatchDto incoming : newPipelineData) {
            validateSingleMatch(incoming, collector);
        }

        ShadowValidationResult result = collector.build();

        // Log summary
        log.info("Shadow validation complete: {}", result.getSummary());

        if (result.hasCriticalDifferences()) {
            log.warn("⚠️ Critical differences found in shadow validation!");
            result.getCriticalDifferences().forEach(diff ->
                log.warn("  - {}: {} = {} vs {}",
                    diff.getMatchBusinessKey(), diff.getField(),
                    diff.getExpectedValue(), diff.getActualValue())
            );
        }

        return result;
    }

    /**
     * Validate a single match against existing data.
     */
    private void validateSingleMatch(InternalMatchDto incoming,
                                     ShadowValidationResult.Collector collector) {
        Optional<Match> existing = findExistingMatch(incoming);

        if (existing.isEmpty()) {
            // New match not in DB - this is expected for upcoming matches
            if (incoming.isScheduled()) {
                // Scheduled matches being new is fine
                collector.recordNewMatch(incoming);
            } else {
                // Completed match not found might be an issue
                log.debug("Completed match not found in DB: {} vs {} on {}",
                    incoming.getHomeTeam(), incoming.getAwayTeam(), incoming.getMatchDate());
                collector.recordNewMatch(incoming);
            }
            return;
        }

        // Compare data
        Match existingMatch = existing.get();
        ComparisonResult comparison = compareMatch(existingMatch, incoming);

        if (comparison.isExactMatch()) {
            collector.recordExactMatch();
        } else if (comparison.hasCriticalDifferences()) {
            comparison.getCriticalDifferences().forEach(collector::recordCriticalDiff);
        } else {
            comparison.getMinorDifferences().forEach(collector::recordMinorDiff);
        }
    }

    /**
     * Find existing match in database.
     */
    private Optional<Match> findExistingMatch(InternalMatchDto dto) {
        // Try exact match first
        List<Match> matches = matchRepository.findByTeamBeforeDate(
            dto.getHomeTeam(),
            dto.getMatchDate().plusDays(1)
        );

        Optional<Match> exact = matches.stream()
            .filter(m -> m.getMatchDate().equals(dto.getMatchDate()))
            .filter(m -> m.getAwayTeam().equalsIgnoreCase(dto.getAwayTeam()))
            .findFirst();

        if (exact.isPresent()) {
            return exact;
        }

        // Try case-insensitive search
        List<Match> caseInsensitive = matchRepository.findByTeamBeforeDateIgnoreCase(
            dto.getHomeTeam(),
            dto.getMatchDate().plusDays(1)
        );

        return caseInsensitive.stream()
            .filter(m -> m.getMatchDate().equals(dto.getMatchDate()))
            .filter(m -> m.getAwayTeam().equalsIgnoreCase(dto.getAwayTeam()))
            .findFirst();
    }

    /**
     * Compare existing match with incoming data.
     */
    private ComparisonResult compareMatch(Match existing, InternalMatchDto incoming) {
        ComparisonResult result = new ComparisonResult(incoming.getBusinessKey());

        // Critical comparisons (would affect business logic)

        // Score comparison
        if (!Objects.equals(existing.getFullTimeHomeGoals(), incoming.getFullTimeHomeGoals())) {
            result.addCriticalDifference(
                "fullTimeHomeGoals",
                String.valueOf(existing.getFullTimeHomeGoals()),
                String.valueOf(incoming.getFullTimeHomeGoals()),
                ShadowDifference.DifferenceType.SCORE_MISMATCH
            );
        }

        if (!Objects.equals(existing.getFullTimeAwayGoals(), incoming.getFullTimeAwayGoals())) {
            result.addCriticalDifference(
                "fullTimeAwayGoals",
                String.valueOf(existing.getFullTimeAwayGoals()),
                String.valueOf(incoming.getFullTimeAwayGoals()),
                ShadowDifference.DifferenceType.SCORE_MISMATCH
            );
        }

        // Result comparison
        if (!Objects.equals(existing.getFullTimeResult(), incoming.getFullTimeResult())) {
            // Only critical if both have a result
            if (existing.getFullTimeResult() != null && incoming.getFullTimeResult() != null) {
                result.addCriticalDifference(
                    "fullTimeResult",
                    existing.getFullTimeResult(),
                    incoming.getFullTimeResult(),
                    ShadowDifference.DifferenceType.RESULT_MISMATCH
                );
            }
        }

        // Date mismatch is critical
        if (!existing.getMatchDate().equals(incoming.getMatchDate())) {
            result.addCriticalDifference(
                "matchDate",
                existing.getMatchDate().toString(),
                incoming.getMatchDate().toString(),
                ShadowDifference.DifferenceType.DATE_MISMATCH
            );
        }

        // Minor comparisons (informational only)

        // Team name differences (might be normalization)
        if (!existing.getHomeTeam().equalsIgnoreCase(incoming.getHomeTeam())) {
            result.addMinorDifference("homeTeam: " + existing.getHomeTeam() +
                " vs " + incoming.getHomeTeam());
        }

        if (!existing.getAwayTeam().equalsIgnoreCase(incoming.getAwayTeam())) {
            result.addMinorDifference("awayTeam: " + existing.getAwayTeam() +
                " vs " + incoming.getAwayTeam());
        }

        // Statistics differences are minor
        if (!Objects.equals(existing.getHomeShots(), incoming.getHomeShots())) {
            result.addMinorDifference("homeShots: " + existing.getHomeShots() +
                " vs " + incoming.getHomeShots());
        }

        return result;
    }

    /**
     * Internal class for collecting comparison results.
     */
    private static class ComparisonResult {
        private final String businessKey;
        private final java.util.List<ShadowDifference> criticalDifferences = new java.util.ArrayList<>();
        private final java.util.List<String> minorDifferences = new java.util.ArrayList<>();

        ComparisonResult(String businessKey) {
            this.businessKey = businessKey;
        }

        void addCriticalDifference(String field, String expected, String actual,
                                   ShadowDifference.DifferenceType type) {
            criticalDifferences.add(ShadowDifference.builder()
                .matchBusinessKey(businessKey)
                .field(field)
                .expectedValue(expected)
                .actualValue(actual)
                .type(type)
                .build());
        }

        void addMinorDifference(String detail) {
            minorDifferences.add(detail);
        }

        boolean isExactMatch() {
            return criticalDifferences.isEmpty() && minorDifferences.isEmpty();
        }

        boolean hasCriticalDifferences() {
            return !criticalDifferences.isEmpty();
        }

        java.util.List<ShadowDifference> getCriticalDifferences() {
            return criticalDifferences;
        }

        java.util.List<String> getMinorDifferences() {
            return minorDifferences;
        }
    }
}

