package com.app.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Status of the API-Football daily quota.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiQuotaStatusDTO {

    private int dailyLimit;
    private int used;
    private int remaining;
    private String resetsAt; // next UTC midnight ISO-8601

    private int injuryBudget;
    private int fixtureBudget;
    private int reserveBudget;
    private boolean isEnabled;
}

