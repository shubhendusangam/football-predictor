package com.app.footballprediction.modeltraining;

import lombok.Builder;
import lombok.Data;

/**
 * Result of comparing multiple models.
 */
@Data
@Builder
public class ModelComparisonResult {

    /**
     * Name of the model.
     */
    private String modelName;

    /**
     * Cross-validation accuracy (0-100).
     */
    private double accuracy;

    /**
     * Cohen's Kappa statistic.
     */
    private double kappa;

    /**
     * Weighted F-measure.
     */
    private double fMeasure;

    /**
     * Weighted precision.
     */
    private double precision;

    /**
     * Weighted recall.
     */
    private double recall;

    /**
     * Area under ROC curve.
     */
    private double areaUnderROC;

    /**
     * Time taken to train/evaluate in milliseconds.
     */
    private long trainingTimeMs;

    /**
     * Generate a formatted row for comparison table.
     */
    public String toRow() {
        return String.format("  %-20s | %6.2f%% | %6.4f | %6.4f | %6.4f | %6.4f | %6dms",
                modelName, accuracy, kappa, fMeasure, precision, recall, trainingTimeMs);
    }
}

