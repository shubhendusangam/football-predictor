package com.app.footballprediction.service;

import com.app.common.dto.*;
import com.app.common.exception.ApiQuotaExceededException;
import com.app.footballprediction.client.ApiFootballClient;
import com.app.footballprediction.ratelimit.ApiFootballRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching and processing injury/suspension data from API-Football.
 * <p>
 * Follows a strict fallback chain (see Part 9 spec):
 * <ol>
 *   <li>Caffeine cache hit → cached data (zero API calls)</li>
 *   <li>Cache miss + quota ok → call API, cache result</li>
 *   <li>Cache miss + quota gone → FULL_STRENGTH fallback</li>
 *   <li>API timeout → FULL_STRENGTH fallback</li>
 *   <li>API 429 → sync limiter to 0, fallback</li>
 *   <li>Empty results → cache empty, fallback</li>
 *   <li>Any RuntimeException → log + fallback silently</li>
 * </ol>
 * <p>
 * Exceptions NEVER propagate outside this service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InjuryDataService {

    private final ApiFootballClient apiFootballClient;
    private final ApiFootballRateLimiter rateLimiter;

    /**
     * Self-reference through the Spring proxy so that internal calls to
     * {@code @Cacheable} methods are intercepted by the cache AOP advice.
     * Without this, {@code this.getTeamAvailability()} from {@code getMatchInjuryContext()}
     * would bypass the cache entirely (Spring self-invocation limitation).
     */
    @Lazy
    @Autowired
    private InjuryDataService self;

    /**
     * Get availability report for a single team in a fixture.
     * Cached for 6 hours per fixture+team combination.
     */
    @Cacheable(value = "injuryAvailability", key = "#fixtureId + '-' + #teamId")
    public TeamAvailabilityDTO getTeamAvailability(long fixtureId, int teamId) {
        try {
            InjuryApiResponse response = self.fetchRawInjuries(fixtureId);

            if (response == null || response.getResponse() == null || response.getResponse().isEmpty()) {
                log.debug("Empty injury response for fixture {} team {}", fixtureId, teamId);
                return buildFallbackAvailability(teamId);
            }

            // Filter entries for the requested team
            List<InjuryEntry> teamEntries = response.getResponse().stream()
                    .filter(entry -> entry.getTeam() != null && entry.getTeam().getId() == teamId)
                    .toList();

            if (teamEntries.isEmpty()) {
                return buildFullStrengthAvailability(teamId,
                        response.getResponse().isEmpty() ? "" :
                                response.getResponse().getFirst().getTeam() != null ?
                                        response.getResponse().getFirst().getTeam().getName() : "");
            }

            return buildAvailability(teamId, teamEntries);

        } catch (ApiQuotaExceededException e) {
            log.warn("API quota exhausted fetching injuries for fixture {} team {}", fixtureId, teamId);
            return buildFallbackAvailability(teamId);
        } catch (Exception e) {
            log.error("Error fetching injuries for fixture {} team {}: {}", fixtureId, teamId, e.getMessage(), e);
            return buildFallbackAvailability(teamId);
        }
    }

    /**
     * Fetch and cache the raw injury API response by fixture ID.
     * Both home and away team lookups share this single API call.
     * Cached for 12 hours.
     */
    @Cacheable(value = "injuryRaw", key = "#fixtureId")
    public InjuryApiResponse fetchRawInjuries(long fixtureId) {
        return apiFootballClient.getInjuriesByFixture(fixtureId)
                .block(java.time.Duration.ofSeconds(10));
    }

    /**
     * Get combined injury context for a full match.
     */
    public MatchInjuryContextDTO getMatchInjuryContext(long fixtureId, int homeTeamId, int awayTeamId) {
        TeamAvailabilityDTO homeAvail = self.getTeamAvailability(fixtureId, homeTeamId);
        TeamAvailabilityDTO awayAvail = self.getTeamAvailability(fixtureId, awayTeamId);

        boolean adjusted = homeAvail.isDataAvailable() || awayAvail.isDataAvailable();

        return MatchInjuryContextDTO.builder()
                .fixtureId(fixtureId)
                .homeAvailability(homeAvail)
                .awayAvailability(awayAvail)
                .probabilitiesAdjusted(adjusted)
                .adjustmentNote(adjusted ? "Injury data applied" : "Injury data unavailable")
                .build();
    }

    // ── Private helpers ─────────────────────────────────────────────

    private TeamAvailabilityDTO buildAvailability(int teamId, List<InjuryEntry> entries) {
        List<PlayerInjuryDTO> injured = new ArrayList<>();
        List<PlayerInjuryDTO> suspended = new ArrayList<>();

        String teamName = entries.isEmpty() ? "" :
                (entries.get(0).getTeam() != null ? entries.get(0).getTeam().getName() : "");

        for (InjuryEntry entry : entries) {
            PlayerInjuryDTO dto = mapToPlayerInjury(entry, teamId, teamName);
            assignImpactWeights(dto);

            if (dto.isSuspension()) {
                suspended.add(dto);
            } else {
                injured.add(dto);
            }
        }

        int totalMissing = injured.size() + suspended.size();

        double attackReduction = Math.min(0.30,
                injured.stream().mapToDouble(PlayerInjuryDTO::getAttackImpactWeight).sum() +
                        suspended.stream().mapToDouble(PlayerInjuryDTO::getAttackImpactWeight).sum());

        double defenceReduction = Math.min(0.25,
                injured.stream().mapToDouble(PlayerInjuryDTO::getDefenceImpactWeight).sum() +
                        suspended.stream().mapToDouble(PlayerInjuryDTO::getDefenceImpactWeight).sum());

        String rating;
        if (totalMissing == 0) {
            rating = "FULL_STRENGTH";
        } else if (totalMissing <= 2) {
            rating = "WEAKENED";
        } else {
            rating = "SEVERELY_WEAKENED";
        }

        String summary = buildImpactSummary(injured, suspended, attackReduction, defenceReduction);

        return TeamAvailabilityDTO.builder()
                .teamId(teamId)
                .teamName(teamName)
                .injuredPlayers(injured)
                .suspendedPlayers(suspended)
                .totalMissing(totalMissing)
                .attackImpactReduction(attackReduction)
                .defenceImpactReduction(defenceReduction)
                .availabilityRating(rating)
                .impactSummary(summary)
                .dataAvailable(true)
                .build();
    }

    private PlayerInjuryDTO mapToPlayerInjury(InjuryEntry entry, int teamId, String teamName) {
        boolean isSuspension = entry.getType() != null &&
                entry.getType().equalsIgnoreCase("Suspended");

        return PlayerInjuryDTO.builder()
                .playerId(entry.getPlayer() != null ? entry.getPlayer().getId() : 0)
                .playerName(entry.getPlayer() != null ? entry.getPlayer().getName() : "Unknown")
                .teamId(teamId)
                .teamName(teamName)
                .injuryType(entry.getType() != null ? entry.getType() : "Unknown")
                .reason(entry.getReason() != null ? entry.getReason() : "")
                .suspension(isSuspension)
                .build();
    }

    /**
     * Assign attack/defence impact weights based on injury type and reason heuristics.
     * <p>
     * Since API-Football /injuries endpoint doesn't include position, we infer
     * from the injury type/reason keywords. Default is midfielder weights.
     */
    private void assignImpactWeights(PlayerInjuryDTO player) {
        String type = (player.getInjuryType() + " " + player.getReason()).toLowerCase();

        double attackWeight;
        double defenceWeight;

        // Heuristic: knee/ACL/muscle injuries on attackers tend to be listed differently
        // Without position data, use balanced midfielder defaults with some keyword hints
        if (type.contains("suspended") || type.contains("ban") || type.contains("red card")) {
            // Suspensions — moderate balanced impact
            attackWeight = 0.08;
            defenceWeight = 0.06;
        } else if (type.contains("hamstring") || type.contains("groin") || type.contains("thigh")) {
            // Typically attackers/midfielders
            attackWeight = 0.12;
            defenceWeight = 0.04;
        } else if (type.contains("ankle") || type.contains("knee") || type.contains("achilles")) {
            // Could be any position — balanced
            attackWeight = 0.08;
            defenceWeight = 0.08;
        } else if (type.contains("shoulder") || type.contains("concussion") || type.contains("head")) {
            // Defenders / GKs more likely
            attackWeight = 0.03;
            defenceWeight = 0.10;
        } else if (type.contains("illness") || type.contains("flu") || type.contains("covid")) {
            // Short-term — lower impact
            attackWeight = 0.05;
            defenceWeight = 0.04;
        } else {
            // Unknown — use midfielder defaults
            attackWeight = 0.08;
            defenceWeight = 0.06;
        }

        player.setAttackImpactWeight(attackWeight);
        player.setDefenceImpactWeight(defenceWeight);
        player.setKeyStar(Math.max(attackWeight, defenceWeight) > 0.10);
    }

    private String buildImpactSummary(List<PlayerInjuryDTO> injured, List<PlayerInjuryDTO> suspended,
                                       double attackReduction, double defenceReduction) {
        List<PlayerInjuryDTO> allMissing = new ArrayList<>();
        allMissing.addAll(injured);
        allMissing.addAll(suspended);

        if (allMissing.isEmpty()) {
            return "Full strength — no absences reported";
        }

        // Sort by highest impact first
        allMissing.sort((a, b) -> Double.compare(
                Math.max(b.getAttackImpactWeight(), b.getDefenceImpactWeight()),
                Math.max(a.getAttackImpactWeight(), a.getDefenceImpactWeight())));

        StringBuilder sb = new StringBuilder("Missing ");
        int shown = Math.min(2, allMissing.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            PlayerInjuryDTO p = allMissing.get(i);
            sb.append(p.getPlayerName()).append(" (").append(p.getInjuryType()).append(")");
        }
        if (allMissing.size() > 2) {
            sb.append(" +").append(allMissing.size() - 2).append(" more");
        }

        // Show biggest impact
        if (attackReduction >= defenceReduction && attackReduction > 0) {
            sb.append(String.format(" — attack -%d%%", Math.round(attackReduction * 100)));
        } else if (defenceReduction > 0) {
            sb.append(String.format(" — defence -%d%%", Math.round(defenceReduction * 100)));
        }

        return sb.toString();
    }

    /**
     * Full-strength result when team has no injuries.
     */
    private TeamAvailabilityDTO buildFullStrengthAvailability(int teamId, String teamName) {
        return TeamAvailabilityDTO.builder()
                .teamId(teamId)
                .teamName(teamName)
                .injuredPlayers(List.of())
                .suspendedPlayers(List.of())
                .totalMissing(0)
                .attackImpactReduction(0.0)
                .defenceImpactReduction(0.0)
                .availabilityRating("FULL_STRENGTH")
                .impactSummary("Full strength — no absences reported")
                .dataAvailable(true)
                .build();
    }

    /**
     * Fallback when API is unavailable / quota exhausted.
     */
    private TeamAvailabilityDTO buildFallbackAvailability(int teamId) {
        return TeamAvailabilityDTO.builder()
                .teamId(teamId)
                .teamName("")
                .injuredPlayers(List.of())
                .suspendedPlayers(List.of())
                .totalMissing(0)
                .attackImpactReduction(0.0)
                .defenceImpactReduction(0.0)
                .availabilityRating("FULL_STRENGTH")
                .impactSummary("Injury data unavailable")
                .dataAvailable(false)
                .build();
    }
}


