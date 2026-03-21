package com.app.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal DTO representing a single player's injury/suspension impact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerInjuryDTO {

    private int playerId;
    private String playerName;
    private int teamId;
    private String teamName;
    private String injuryType;
    private String reason;

    /** True if the absence is a suspension rather than injury */
    @Builder.Default
    private boolean suspension = false;

    /** True if this player has high impact weight (>0.10) */
    @Builder.Default
    private boolean keyStar = false;

    /** Impact on team attacking output (0.0 to 0.30) */
    @Builder.Default
    private double attackImpactWeight = 0.0;

    /** Impact on team defensive solidity (0.0 to 0.25) */
    @Builder.Default
    private double defenceImpactWeight = 0.0;
}

