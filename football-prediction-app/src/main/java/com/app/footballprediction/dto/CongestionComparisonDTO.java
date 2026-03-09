package com.app.footballprediction.dto;

import lombok.Builder;
import lombok.Value;

/**
 * DTO for comparing fixture congestion between two teams.
 *
 * @author Football Forecaster Team
 * @version 1.0.0
 */
@Value
@Builder
public class CongestionComparisonDTO {

    FixtureCongestionDTO home;
    FixtureCongestionDTO away;

    /** Which team has the fatigue advantage: "home", "away", or "neutral". */
    String advantageTeam;

    /** Human-readable advantage summary. */
    String advantageSummary;

    /** Difference in fatigue index (home − away). Positive means home is MORE fatigued. */
    int fatigueDifference;
}

