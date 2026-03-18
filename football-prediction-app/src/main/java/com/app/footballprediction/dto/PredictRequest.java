package com.app.footballprediction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Match prediction request")
public class PredictRequest {
   @NotBlank(message = "Home team name is required")
   @Size(min = 2, max = 50, message = "Home team name must be between 2 and 50 characters")
   @Schema(description = "Name of the home team", example = "Arsenal")
   private String homeTeam;

   @NotBlank(message = "Away team name is required")
   @Size(min = 2, max = 50, message = "Away team name must be between 2 and 50 characters")
   @Schema(description = "Name of the away team", example = "Chelsea")
   private String awayTeam;
}
