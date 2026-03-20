package com.app.common.model;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * Serializable Poisson model parameters for the Dixon-Coles score prediction model.
 *
 * <p>Shared between the model-training-service (which serializes it)
 * and football-prediction-app (which deserializes and uses it).
 * Both modules MUST use this exact class to avoid serialization mismatches.</p>
 *
 * <h3>Parameters</h3>
 * <ul>
 *   <li>{@code attack}        — per-team attacking strength (normalised, mean ≈ 1.0)</li>
 *   <li>{@code defence}       — per-team defensive weakness (normalised, mean ≈ 1.0)</li>
 *   <li>{@code homeAdvantage} — multiplicative home-field advantage factor</li>
 *   <li>{@code leagueAvgGoals} — average goals per team per match</li>
 *   <li>{@code maxGoals}      — maximum goals considered in the score matrix</li>
 *   <li>{@code trainedAt}     — timestamp of model training</li>
 * </ul>
 */
public class PoissonParameters implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, Double> attack;
    private Map<String, Double> defence;
    private double homeAdvantage;
    private double leagueAvgGoals;
    private int maxGoals;
    private Date trainedAt;

    public PoissonParameters() {
    }

    // ── Getters and Setters ───────────────────────────────────────────

    public Map<String, Double> getAttack() {
        return attack;
    }

    public void setAttack(Map<String, Double> attack) {
        this.attack = attack;
    }

    public Map<String, Double> getDefence() {
        return defence;
    }

    public void setDefence(Map<String, Double> defence) {
        this.defence = defence;
    }

    public double getHomeAdvantage() {
        return homeAdvantage;
    }

    public void setHomeAdvantage(double homeAdvantage) {
        this.homeAdvantage = homeAdvantage;
    }

    public double getLeagueAvgGoals() {
        return leagueAvgGoals;
    }

    public void setLeagueAvgGoals(double leagueAvgGoals) {
        this.leagueAvgGoals = leagueAvgGoals;
    }

    public int getMaxGoals() {
        return maxGoals;
    }

    public void setMaxGoals(int maxGoals) {
        this.maxGoals = maxGoals;
    }

    public Date getTrainedAt() {
        return trainedAt;
    }

    public void setTrainedAt(Date trainedAt) {
        this.trainedAt = trainedAt;
    }
}

