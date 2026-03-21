package com.app.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Combined injury context for a match (home + away availability).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchInjuryContextDTO {

    private long fixtureId;
    private TeamAvailabilityDTO homeAvailability;
    private TeamAvailabilityDTO awayAvailability;

    /** True if probabilities were adjusted based on injury data */
    @Builder.Default
    private boolean probabilitiesAdjusted = false;

    /** Human-readable note about adjustments */
    @Builder.Default
    private String adjustmentNote = "";
}

