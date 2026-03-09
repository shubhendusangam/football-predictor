package com.app.common.util;

/**
 * Utility class containing common helper methods used across the prediction services.
 */
public final class PredictionUtils {

    private PredictionUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Round a double value to 2 decimal places for clean output.
     *
     * @param value the value to round
     * @return the rounded value
     */
    public static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Returns a safe double value, converting NaN or Infinite values to 0.0.
     *
     * @param val the value to check
     * @return 0.0 if NaN or Infinite, otherwise the original value
     */
    public static double safe(double val) {
        return Double.isNaN(val) || Double.isInfinite(val) ? 0.0 : val;
    }

    /**
     * Determines confidence level based on the highest probability in the distribution.
     *
     * <p>Uses entropy-based confidence instead of raw max probability.
     * This accounts for how spread out the probability distribution is:
     * <ul>
     *   <li>[0.60, 0.20, 0.20] → HIGH (clear winner, low entropy)</li>
     *   <li>[0.45, 0.30, 0.25] → MEDIUM (leaning, moderate entropy)</li>
     *   <li>[0.35, 0.33, 0.32] → LOW (nearly uniform, high entropy)</li>
     * </ul>
     *
     * @param probs array of probabilities [homeWin, draw, awayWin]
     * @return confidence level as string: "HIGH", "MEDIUM", or "LOW"
     */
    public static String getConfidence(double[] probs) {
        double max = Math.max(probs[0], Math.max(probs[1], probs[2]));

        // Also compute entropy-based confidence as secondary signal
        double entropy = 0.0;
        for (double p : probs) {
            if (p > 0) {
                entropy -= p * Math.log(p);
            }
        }
        // Max entropy for 3 classes = ln(3) ≈ 1.099
        double normalizedEntropy = entropy / Math.log(3);

        // Combined confidence: high max probability AND low entropy → HIGH
        if (max >= 0.55 && normalizedEntropy < 0.85) return "HIGH";
        if (max >= 0.42 && normalizedEntropy < 0.95) return "MEDIUM";
        return "LOW";
    }

    /**
     * Get a numeric confidence score (0.0 to 1.0) based on how decisive the prediction is.
     * Uses the margin between the top and second-highest probability.
     *
     * @param probs array of probabilities [homeWin, draw, awayWin]
     * @return confidence score from 0.0 (uncertain) to 1.0 (very confident)
     */
    public static double getConfidenceScore(double[] probs) {
        double[] sorted = {probs[0], probs[1], probs[2]};
        java.util.Arrays.sort(sorted);
        // Margin between 1st and 2nd highest probability
        double margin = sorted[2] - sorted[1];
        // Normalize: max margin is ~1.0, min is 0.0
        return Math.min(1.0, margin * 2.0);
    }

    /**
     * Converts match result label to human-readable text.
     *
     * @param label the result label ("H", "D", "A")
     * @return human-readable result ("HOME_WIN", "DRAW", "AWAY_WIN", or "UNKNOWN")
     */
    public static String labelToText(String label) {
        return switch (label) {
            case "H" -> "HOME_WIN";
            case "D" -> "DRAW";
            case "A" -> "AWAY_WIN";
            default -> "UNKNOWN";
        };
    }
}
