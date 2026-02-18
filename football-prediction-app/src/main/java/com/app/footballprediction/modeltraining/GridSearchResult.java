package com.app.footballprediction.modeltraining;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Result of hyperparameter grid search.
 */
@Data
@Builder
public class GridSearchResult {

    /**
     * Name of the classifier (e.g., "RandomForest", "AdaBoostM1").
     */
    private String classifierName;

    /**
     * Best hyperparameter configuration found.
     */
    private Map<String, Object> bestParams;

    /**
     * Cross-validation accuracy with best params (0-100).
     */
    private double accuracy;

    /**
     * Cohen's Kappa with best params.
     */
    private double kappa;

    /**
     * Weighted F-measure with best params.
     */
    private double fMeasure;

    /**
     * Generate a formatted report.
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n══════════════════════════════════════════\n");
        sb.append("   GRID SEARCH RESULTS - ").append(classifierName).append("\n");
        sb.append("══════════════════════════════════════════\n");
        sb.append(String.format("  Best Accuracy : %.2f%%%n", accuracy));
        sb.append(String.format("  Kappa         : %.4f%n", kappa));
        sb.append(String.format("  F-Measure     : %.4f%n", fMeasure));
        sb.append("\n  Best Parameters:\n");

        if (bestParams != null) {
            for (Map.Entry<String, Object> entry : bestParams.entrySet()) {
                sb.append(String.format("    %-20s : %s%n", entry.getKey(), entry.getValue()));
            }
        }

        sb.append("══════════════════════════════════════════\n");
        return sb.toString();
    }
}

