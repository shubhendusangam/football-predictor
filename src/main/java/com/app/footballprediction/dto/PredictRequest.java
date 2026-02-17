package com.app.footballprediction.dto;

import lombok.Data;

@Data
public class PredictRequest {
   private String homeTeam;
   private String awayTeam;
}
