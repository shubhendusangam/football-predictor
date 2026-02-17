package com.app.footballprediction.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PredictRequest DTO.
 */
@DisplayName("PredictRequest DTO Unit Tests")
class PredictRequestTest {

    @Test
    @DisplayName("creates request with home and away team")
    void createsRequestWithTeams() {
        PredictRequest request = new PredictRequest();
        request.setHomeTeam("Arsenal");
        request.setAwayTeam("Chelsea");

        assertThat(request.getHomeTeam()).isEqualTo("Arsenal");
        assertThat(request.getAwayTeam()).isEqualTo("Chelsea");
    }

    @Test
    @DisplayName("allows null teams initially")
    void allowsNullTeams() {
        PredictRequest request = new PredictRequest();

        assertThat(request.getHomeTeam()).isNull();
        assertThat(request.getAwayTeam()).isNull();
    }

    @Test
    @DisplayName("equals and hashCode work correctly")
    void equalsAndHashCode() {
        PredictRequest request1 = new PredictRequest();
        request1.setHomeTeam("Arsenal");
        request1.setAwayTeam("Chelsea");

        PredictRequest request2 = new PredictRequest();
        request2.setHomeTeam("Arsenal");
        request2.setAwayTeam("Chelsea");

        assertThat(request1).isEqualTo(request2);
        assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
    }

    @Test
    @DisplayName("toString includes team names")
    void toStringIncludesTeamNames() {
        PredictRequest request = new PredictRequest();
        request.setHomeTeam("Arsenal");
        request.setAwayTeam("Chelsea");

        String toString = request.toString();

        assertThat(toString).contains("Arsenal");
        assertThat(toString).contains("Chelsea");
    }
}

