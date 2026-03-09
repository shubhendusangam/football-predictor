package com.app.common.ingestion.mapper;

import com.app.common.ingestion.dto.InternalMatchDto;
import com.app.common.ingestion.dto.InternalStandingDto;
import com.app.common.model.Match;
import com.app.common.model.SeasonTeamStats;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Mapper between canonical DTOs and existing entities.
 *
 * <p>This mapper ensures that:
 * <ul>
 *   <li>All existing entity fields are preserved</li>
 *   <li>No schema changes are required</li>
 *   <li>Conversions are bidirectional and lossless</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This mapper does NOT introduce any new fields
 * to existing entities. It only maps between existing structures.
 */
@Component
public class CanonicalMapper {

    // ══════════════════════════════════════════════════════════════
    // Match Entity ↔ InternalMatchDto
    // ══════════════════════════════════════════════════════════════

    /**
     * Convert Match entity to canonical DTO.
     *
     * @param match Match entity
     * @param providerName Source provider name
     * @return Canonical DTO
     */
    public InternalMatchDto toDto(Match match, String providerName) {
        if (match == null) {
            return null;
        }

        return InternalMatchDto.builder()
            .externalId(String.valueOf(match.getId()))
            .providerName(providerName)
            .matchDate(match.getMatchDate())
            .homeTeam(match.getHomeTeam())
            .awayTeam(match.getAwayTeam())
            .season(match.getSeason())
            .competition("PL") // Default to Premier League
            .fullTimeHomeGoals(match.getFullTimeHomeGoals())
            .fullTimeAwayGoals(match.getFullTimeAwayGoals())
            .fullTimeResult(match.getFullTimeResult())
            .halfTimeHomeGoals(match.getHalfTimeHomeGoals())
            .halfTimeAwayGoals(match.getHalfTimeAwayGoals())
            .halfTimeResult(match.getHalfTimeResult())
            .referee(match.getReferee())
            .status(match.getFullTimeResult() != null ? "FINISHED" : "SCHEDULED")
            .kickoffTime(match.getKickoffTime())
            .homeShots(match.getHomeShots())
            .awayShots(match.getAwayShots())
            .homeShotsOnTarget(match.getHomeShotsOnTarget())
            .awayShotsOnTarget(match.getAwayShotsOnTarget())
            .homeCorners(match.getHomeCorners())
            .awayCorners(match.getAwayCorners())
            .homeYellowCards(match.getHomeYellowCards())
            .awayYellowCards(match.getAwayYellowCards())
            .homeRedCards(match.getHomeRedCards())
            .awayRedCards(match.getAwayRedCards())
            .homeFouls(match.getHomeFouls())
            .awayFouls(match.getAwayFouls())
            .homeWinOdds(match.getB365H())
            .drawOdds(match.getB365D())
            .awayWinOdds(match.getB365A())
            .oddsProvider("Bet365")
            .fetchedAt(Instant.now())
            .build();
    }

    /**
     * Convert canonical DTO to new Match entity.
     * Used for inserting new matches.
     *
     * @param dto Canonical DTO
     * @return Match entity
     */
    public Match toEntity(InternalMatchDto dto) {
        if (dto == null) {
            return null;
        }

        return Match.builder()
            .matchDate(dto.getMatchDate())
            .homeTeam(dto.getHomeTeam())
            .awayTeam(dto.getAwayTeam())
            .season(dto.getSeason())
            .fullTimeHomeGoals(dto.getFullTimeHomeGoals())
            .fullTimeAwayGoals(dto.getFullTimeAwayGoals())
            .fullTimeResult(dto.getFullTimeResult())
            .halfTimeHomeGoals(dto.getHalfTimeHomeGoals())
            .halfTimeAwayGoals(dto.getHalfTimeAwayGoals())
            .halfTimeResult(dto.getHalfTimeResult())
            .referee(dto.getReferee())
            .kickoffTime(dto.getKickoffTime())
            .homeShots(dto.getHomeShots())
            .awayShots(dto.getAwayShots())
            .homeShotsOnTarget(dto.getHomeShotsOnTarget())
            .awayShotsOnTarget(dto.getAwayShotsOnTarget())
            .homeCorners(dto.getHomeCorners())
            .awayCorners(dto.getAwayCorners())
            .homeYellowCards(dto.getHomeYellowCards())
            .awayYellowCards(dto.getAwayYellowCards())
            .homeRedCards(dto.getHomeRedCards())
            .awayRedCards(dto.getAwayRedCards())
            .homeFouls(dto.getHomeFouls())
            .awayFouls(dto.getAwayFouls())
            .b365H(dto.getHomeWinOdds())
            .b365D(dto.getDrawOdds())
            .b365A(dto.getAwayWinOdds())
            .statsProcessed(false) // New matches haven't been processed
            .build();
    }

    /**
     * Update existing Match entity with data from DTO.
     * Only updates fields that have changed, preserving existing data.
     *
     * @param existing Existing Match entity
     * @param dto Source DTO with new data
     * @return Updated Match entity (same instance)
     */
    public Match updateEntity(Match existing, InternalMatchDto dto) {
        if (existing == null || dto == null) {
            return existing;
        }

        // Only update result fields if match is now completed
        if (dto.getFullTimeResult() != null && existing.getFullTimeResult() == null) {
            existing.setFullTimeHomeGoals(dto.getFullTimeHomeGoals());
            existing.setFullTimeAwayGoals(dto.getFullTimeAwayGoals());
            existing.setFullTimeResult(dto.getFullTimeResult());
            existing.setStatsProcessed(false); // Mark for reprocessing
        }

        // Update half-time if available
        if (dto.getHalfTimeHomeGoals() != null && existing.getHalfTimeHomeGoals() == null) {
            existing.setHalfTimeHomeGoals(dto.getHalfTimeHomeGoals());
            existing.setHalfTimeAwayGoals(dto.getHalfTimeAwayGoals());
            existing.setHalfTimeResult(dto.getHalfTimeResult());
        }

        // Update statistics if available and not already set
        if (dto.getHomeShots() != null && existing.getHomeShots() == null) {
            existing.setHomeShots(dto.getHomeShots());
            existing.setAwayShots(dto.getAwayShots());
        }
        if (dto.getHomeShotsOnTarget() != null && existing.getHomeShotsOnTarget() == null) {
            existing.setHomeShotsOnTarget(dto.getHomeShotsOnTarget());
            existing.setAwayShotsOnTarget(dto.getAwayShotsOnTarget());
        }
        if (dto.getHomeCorners() != null && existing.getHomeCorners() == null) {
            existing.setHomeCorners(dto.getHomeCorners());
            existing.setAwayCorners(dto.getAwayCorners());
        }
        if (dto.getHomeYellowCards() != null && existing.getHomeYellowCards() == null) {
            existing.setHomeYellowCards(dto.getHomeYellowCards());
            existing.setAwayYellowCards(dto.getAwayYellowCards());
        }
        if (dto.getHomeRedCards() != null && existing.getHomeRedCards() == null) {
            existing.setHomeRedCards(dto.getHomeRedCards());
            existing.setAwayRedCards(dto.getAwayRedCards());
        }
        if (dto.getHomeFouls() != null && existing.getHomeFouls() == null) {
            existing.setHomeFouls(dto.getHomeFouls());
            existing.setAwayFouls(dto.getAwayFouls());
        }

        // Update referee if missing
        if (dto.getReferee() != null && existing.getReferee() == null) {
            existing.setReferee(dto.getReferee());
        }

        // Backfill kick-off time if missing
        if (dto.getKickoffTime() != null && (existing.getKickoffTime() == null || existing.getKickoffTime().isBlank())) {
            existing.setKickoffTime(dto.getKickoffTime());
        }

        return existing;
    }

    // ══════════════════════════════════════════════════════════════
    // SeasonTeamStats ↔ InternalStandingDto
    // ══════════════════════════════════════════════════════════════

    /**
     * Convert SeasonTeamStats entity to canonical standing DTO.
     *
     * @param stats SeasonTeamStats entity
     * @param providerName Source provider name
     * @return Canonical DTO
     */
    public InternalStandingDto toStandingDto(SeasonTeamStats stats, String providerName) {
        if (stats == null) {
            return null;
        }

        return InternalStandingDto.builder()
            .teamName(stats.getTeamName())
            .season(stats.getSeasonId())
            .competition("PL")
            .played(stats.getMatchesPlayed())
            .won(stats.getWins())
            .drawn(stats.getDraws())
            .lost(stats.getLosses())
            .goalsFor(stats.getGoalsScored())
            .goalsAgainst(stats.getGoalsConceded())
            .goalDifference(stats.getGoalsScored() - stats.getGoalsConceded())
            .points(stats.getWins() * 3 + stats.getDraws())
            .form(stats.getFormString())
            .providerName(providerName)
            .fetchedAt(Instant.now())
            .build();
    }
}

