package com.app.footballprediction.exception;

/**
 * Thrown when a team name cannot be resolved to a known team in the database.
 */
public class TeamNotFoundException extends RuntimeException {

    private final String teamName;

    public TeamNotFoundException(String teamName) {
        super("Team not found: " + teamName);
        this.teamName = teamName;
    }

    public TeamNotFoundException(String teamName, String message) {
        super(message);
        this.teamName = teamName;
    }

    public String getTeamName() {
        return teamName;
    }
}

