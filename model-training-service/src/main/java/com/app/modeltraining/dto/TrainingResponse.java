package com.app.modeltraining.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingResponse {
    private boolean success;
    private String message;
    private String report;
    private Long trainingTimeMs;
}

