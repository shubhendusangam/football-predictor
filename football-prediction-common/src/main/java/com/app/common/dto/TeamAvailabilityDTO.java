package com.app.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated availability report for a single team in a fixture.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamAvailabilityDTO {

    private int teamId;
    private String teamName;

    @Builder.Default
    private List<PlayerInjuryDTO> injuredPlayers = List.of();

    @Builder.Default
    private List<PlayerInjuryDTO> suspendedPlayers = List.of();

    /** Total count of injured + suspended players */
    @Builder.Default
    private int totalMissing = 0;

    /** Aggregate attack penalty (0.0 to 0.30, capped) */
    @Builder.Default
    private double attackImpactReduction = 0.0;

    /** Aggregate defence penalty (0.0 to 0.25, capped) */
    @Builder.Default
    private double defenceImpactReduction = 0.0;

    /** FULL_STRENGTH / WEAKENED / SEVERELY_WEAKENED */
    @Builder.Default
    private String availabilityRating = "FULL_STRENGTH";

    /** Human-readable summary, e.g. "Missing Saka (Hamstring) — attack -15%" */
    @Builder.Default
    private String impactSummary = "";

    /** False when API call failed or quota exhausted */
    @Builder.Default
    private boolean dataAvailable = false;
}

