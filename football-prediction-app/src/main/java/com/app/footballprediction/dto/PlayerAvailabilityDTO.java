package com.app.footballprediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO representing a team's current squad availability / fitness status.
 * Included in prediction responses to explain player absence impact.
 */
@Data
@Builder
@Schema(description = "Team squad availability and player absence impact")
public class PlayerAvailabilityDTO {

    @Schema(description = "Team name", example = "Chelsea")
    private String teamName;

    @Schema(description = "Squad strength (0.0-1.0, 1.0 = full strength)", example = "0.82")
    private double squadStrength;

    @Schema(description = "Attack impact reduction (0.0 = no impact, 1.0 = full attack lost)", example = "0.15")
    private double attackImpact;

    @Schema(description = "Defence impact reduction (0.0 = no impact, 1.0 = full defence lost)", example = "0.08")
    private double defenceImpact;

    @Schema(description = "Impact severity rating", example = "WEAKENED",
            allowableValues = {"FULL_STRENGTH", "MINOR_CONCERNS", "WEAKENED", "SEVERELY_WEAKENED"})
    private String availabilityRating;

    @Schema(description = "List of absent/doubtful players")
    private List<AbsentPlayerDTO> absentPlayers;

    @Schema(description = "Human-readable availability note", example = "Chelsea missing Reece James (injury)")
    private String availabilityNote;

    /**
     * Single absent player.
     */
    @Data
    @Builder
    public static class AbsentPlayerDTO {
        @Schema(description = "Player name", example = "Reece James")
        private String playerName;

        @Schema(description = "Player position", example = "DEF")
        private String position;

        @Schema(description = "Absence status", example = "INJURED",
                allowableValues = {"INJURED", "SUSPENDED", "DOUBTFUL"})
        private String status;

        @Schema(description = "Reason for absence", example = "Hamstring injury")
        private String reason;

        @Schema(description = "Expected return date (ISO format, null if unknown)", example = "2026-04-15")
        private String expectedReturn;

        @Schema(description = "Player importance (1-10)", example = "8")
        private int importanceRating;

        @Schema(description = "Whether this is a designated key star", example = "true")
        private boolean keyStar;
    }
}

