package com.app.footballprediction.modeltraining;

import lombok.Builder;
import lombok.Data;

/**
 * Result of k-fold cross-validation.
 */
@Data
@Builder
public class CrossValidationResult {

    /**
     * Overall accuracy percentage (0-100).
     */
    private double accuracy;

    /**
     * Cohen's Kappa statistic (-1 to 1, higher is better).
     * Measures agreement adjusted for chance.
     */
    private double kappa;

    /**
     * Mean absolute error.
     */
    private double meanAbsoluteError;

    /**
     * Root mean squared error.
     */
    private double rootMeanSquaredError;

    /**
     * Weighted F-measure (harmonic mean of precision and recall).
     */
    private double fMeasure;

    /**
     * Weighted precision (true positives / (true positives + false positives)).
     */
    private double precision;

    /**
     * Weighted recall (true positives / (true positives + false negatives)).
     */
    private double recall;

    /**
     * Area under the ROC curve (0.5 = random, 1.0 = perfect).
     */
    private double areaUnderROC;

    /**
     * Confusion matrix: [actual][predicted].
     */
    private double[][] confusionMatrix;

    /**
     * Number of folds used.
     */
    private int folds;

    /**
     * Per-class statistics as formatted string.
     */
    private String classDetails;

    /**
     * Generate a formatted report.
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n══════════════════════════════════════════\n");
        sb.append("   CROSS-VALIDATION RESULTS (").append(folds).append("-Fold)\n");
        sb.append("══════════════════════════════════════════\n");
        sb.append(String.format("  Accuracy      : %.2f%%%n", accuracy));
        sb.append(String.format("  Kappa         : %.4f%n", kappa));
        sb.append(String.format("  F-Measure     : %.4f%n", fMeasure));
        sb.append(String.format("  Precision     : %.4f%n", precision));
        sb.append(String.format("  Recall        : %.4f%n", recall));
        sb.append(String.format("  AUC (ROC)     : %.4f%n", areaUnderROC));
        sb.append(String.format("  MAE           : %.4f%n", meanAbsoluteError));
        sb.append(String.format("  RMSE          : %.4f%n", rootMeanSquaredError));
        sb.append("\n  Per-class breakdown:\n");
        sb.append(classDetails);
        sb.append("\n  Confusion Matrix:\n");
        sb.append(formatConfusionMatrix());
        sb.append("══════════════════════════════════════════\n");
        return sb.toString();
    }

    private String formatConfusionMatrix() {
        if (confusionMatrix == null) return "  N/A\n";

        StringBuilder sb = new StringBuilder();
        String[] labels = {"H", "D", "A"};

        // Header
        sb.append("       ");
        for (String label : labels) {
            sb.append(String.format("%6s", label));
        }
        sb.append("  <- predicted\n");

        // Rows
        for (int i = 0; i < confusionMatrix.length && i < labels.length; i++) {
            sb.append(String.format("  %4s ", labels[i]));
            for (int j = 0; j < confusionMatrix[i].length && j < labels.length; j++) {
                sb.append(String.format("%6.0f", confusionMatrix[i][j]));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

