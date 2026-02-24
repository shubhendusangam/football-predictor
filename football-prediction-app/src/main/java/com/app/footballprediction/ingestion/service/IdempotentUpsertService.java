package com.app.footballprediction.ingestion.service;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.mapper.CanonicalMapper;
import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.ingestion.model.UpsertResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service for performing idempotent upsert operations on matches.
 *
 * <p>This service ensures:
 * <ul>
 *   <li>No duplicate matches are created</li>
 *   <li>Existing matches are only updated if data changed</li>
 *   <li>All operations are safe to retry</li>
 *   <li>Uses EXISTING unique constraint (matchDate, homeTeam, awayTeam)</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This service uses existing MatchRepository methods
 * and does NOT introduce any new database constraints or indexes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotentUpsertService {

    private final MatchRepository matchRepository;
    private final CanonicalMapper mapper;

    /**
     * Upsert a batch of matches with idempotency guarantees.
     *
     * @param matches Matches to upsert (in canonical format)
     * @return Result containing insert/update/skip counts
     */
    @Transactional
    public UpsertResult upsertMatches(List<InternalMatchDto> matches) {
        log.info("Starting idempotent upsert for {} matches", matches.size());

        UpsertResult.UpsertResultCollector collector = new UpsertResult.UpsertResultCollector();

        for (InternalMatchDto dto : matches) {
            try {
                UpsertOutcome outcome = upsertSingleMatch(dto);

                switch (outcome.type) {
                    case INSERTED -> collector.recordInsert(outcome.match);
                    case UPDATED -> collector.recordUpdate(outcome.match);
                    case SKIPPED -> collector.recordSkipped(dto.getBusinessKey(), outcome.reason);
                }

            } catch (Exception e) {
                log.error("Failed to upsert match {}: {}", dto.getBusinessKey(), e.getMessage());
                collector.recordError(dto.getBusinessKey(), e.getMessage());
            }
        }

        UpsertResult result = collector.build();
        log.info("Upsert complete: {} inserted, {} updated, {} skipped, {} errors",
            result.getInserted(), result.getUpdated(), result.getSkipped(),
            result.getErrors().size());

        return result;
    }

    /**
     * Upsert a single match.
     *
     * @param dto Match data in canonical format
     * @return Outcome of the operation
     */
    public UpsertOutcome upsertSingleMatch(InternalMatchDto dto) {
        // Validate required fields
        if (dto.getMatchDate() == null || dto.getHomeTeam() == null || dto.getAwayTeam() == null) {
            return UpsertOutcome.skipped("Missing required fields (date, homeTeam, or awayTeam)");
        }

        // Check for existing match using existing repository method
        Optional<Match> existing = findExistingMatch(dto);

        if (existing.isPresent()) {
            Match existingMatch = existing.get();

            // Check if update is needed
            if (requiresUpdate(existingMatch, dto)) {
                Match updated = updateExistingMatch(existingMatch, dto);
                Match saved = matchRepository.save(updated);
                log.debug("Updated match: {} vs {} on {}",
                    dto.getHomeTeam(), dto.getAwayTeam(), dto.getMatchDate());
                return UpsertOutcome.updated(saved);
            }

            return UpsertOutcome.skipped("No changes detected");
        }

        // New match - insert
        Match newMatch = mapper.toEntity(dto);
        Match saved = matchRepository.save(newMatch);
        log.debug("Inserted new match: {} vs {} on {}",
            dto.getHomeTeam(), dto.getAwayTeam(), dto.getMatchDate());
        return UpsertOutcome.inserted(saved);
    }

    /**
     * Find existing match using existing repository method.
     * Uses the same deduplication logic as CsvIngestionService.
     */
    private Optional<Match> findExistingMatch(InternalMatchDto dto) {
        // First, quick existence check using existing method
        boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
            dto.getMatchDate(),
            dto.getHomeTeam(),
            dto.getAwayTeam()
        );

        if (!exists) {
            // Try case-insensitive search
            List<Match> matches = matchRepository.findByTeamBeforeDateIgnoreCase(
                dto.getHomeTeam(),
                dto.getMatchDate().plusDays(1)
            );

            return matches.stream()
                .filter(m -> m.getMatchDate().equals(dto.getMatchDate()))
                .filter(m -> m.getAwayTeam().equalsIgnoreCase(dto.getAwayTeam()))
                .findFirst();
        }

        // Fetch the actual entity for comparison
        List<Match> matches = matchRepository.findByTeamBeforeDate(
            dto.getHomeTeam(),
            dto.getMatchDate().plusDays(1)
        );

        return matches.stream()
            .filter(m -> m.getMatchDate().equals(dto.getMatchDate()))
            .filter(m -> m.getAwayTeam().equalsIgnoreCase(dto.getAwayTeam()))
            .findFirst();
    }

    /**
     * Check if an update is needed by comparing key fields.
     * Only updates for meaningful changes to avoid unnecessary writes.
     */
    private boolean requiresUpdate(Match existing, InternalMatchDto incoming) {
        // Result was added (match completed)
        if (existing.getFullTimeResult() == null && incoming.getFullTimeResult() != null) {
            log.debug("Match completed: {} vs {}", incoming.getHomeTeam(), incoming.getAwayTeam());
            return true;
        }

        // Score correction (rare but important)
        if (incoming.getFullTimeResult() != null) {
            if (!Objects.equals(existing.getFullTimeHomeGoals(), incoming.getFullTimeHomeGoals()) ||
                !Objects.equals(existing.getFullTimeAwayGoals(), incoming.getFullTimeAwayGoals())) {
                log.info("Score correction detected for {} vs {}: {}:{} → {}:{}",
                    incoming.getHomeTeam(), incoming.getAwayTeam(),
                    existing.getFullTimeHomeGoals(), existing.getFullTimeAwayGoals(),
                    incoming.getFullTimeHomeGoals(), incoming.getFullTimeAwayGoals());
                return true;
            }
        }

        // Statistics were added
        if (hasNewStatistics(existing, incoming)) {
            return true;
        }

        return false;
    }

    /**
     * Check if incoming data has statistics that existing doesn't have.
     */
    private boolean hasNewStatistics(Match existing, InternalMatchDto incoming) {
        // Check if any stats field is being added
        return (incoming.getHomeShots() != null && existing.getHomeShots() == null) ||
               (incoming.getHomeShotsOnTarget() != null && existing.getHomeShotsOnTarget() == null) ||
               (incoming.getHomeCorners() != null && existing.getHomeCorners() == null) ||
               (incoming.getHalfTimeHomeGoals() != null && existing.getHalfTimeHomeGoals() == null);
    }

    /**
     * Update existing match entity with new data.
     * Uses mapper to apply changes safely.
     */
    private Match updateExistingMatch(Match existing, InternalMatchDto incoming) {
        return mapper.updateEntity(existing, incoming);
    }

    /**
     * Outcome of an upsert operation.
     */
    public static class UpsertOutcome {
        public final OutcomeType type;
        public final Match match;
        public final String reason;

        private UpsertOutcome(OutcomeType type, Match match, String reason) {
            this.type = type;
            this.match = match;
            this.reason = reason;
        }

        public static UpsertOutcome inserted(Match match) {
            return new UpsertOutcome(OutcomeType.INSERTED, match, null);
        }

        public static UpsertOutcome updated(Match match) {
            return new UpsertOutcome(OutcomeType.UPDATED, match, null);
        }

        public static UpsertOutcome skipped(String reason) {
            return new UpsertOutcome(OutcomeType.SKIPPED, null, reason);
        }

        public enum OutcomeType {
            INSERTED, UPDATED, SKIPPED
        }
    }
}

