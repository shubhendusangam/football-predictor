package com.app.footballprediction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PredictRequest {
   @NotBlank(message = "Home team name is required")
   @Size(min = 2, max = 50, message = "Home team name must be between 2 and 50 characters")
   private String homeTeam;

   @NotBlank(message = "Away team name is required")
   @Size(min = 2, max = 50, message = "Away team name must be between 2 and 50 characters")
   private String awayTeam;
}
