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
     * HIGH   → model is fairly sure (>= 0.55)
     * MEDIUM → model leans one way but not strongly (>= 0.45)
     * LOW    → all three outcomes nearly equal — don't trust this one
     *
     * @param probs array of probabilities [homeWin, draw, awayWin]
     * @return confidence level as string: "HIGH", "MEDIUM", or "LOW"
     */
    public static String getConfidence(double[] probs) {
        double max = Math.max(probs[0], Math.max(probs[1], probs[2]));
        if (max >= 0.55) return "HIGH";
        if (max >= 0.45) return "MEDIUM";
        return "LOW";
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

