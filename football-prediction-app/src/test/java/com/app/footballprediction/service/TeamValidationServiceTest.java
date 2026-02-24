package com.app.footballprediction.service;

import com.app.common.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TeamValidationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeamValidationService Unit Tests")
class TeamValidationServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private TeamValidationService teamValidationService;

    @BeforeEach
    void setUp() {
        // Setup common test data
    }

    @Nested
    @DisplayName("validateTeam()")
    class ValidateTeamTests {

        @Test
        @DisplayName("returns invalid for null team name")
        void returnsInvalidForNull() {
            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("cannot be empty");
        }

        @Test
        @DisplayName("returns invalid for blank team name")
        void returnsInvalidForBlank() {
            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("   ");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrorMessage()).contains("cannot be empty");
        }

        @Test
        @DisplayName("normalizes common aliases like Spurs to Tottenham")
        void normalizesCommonAliases() {
            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("Spurs");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getNormalizedName()).isEqualTo("Tottenham");
        }

        @Test
        @DisplayName("normalizes Man Utd to Man United")
        void normalizesManUtd() {
            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("Man Utd");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getNormalizedName()).isEqualTo("Man United");
        }

        @Test
        @DisplayName("normalizes API format names like Arsenal FC")
        void normalizesApiFormat() {
            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("Arsenal FC");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getNormalizedName()).isEqualTo("Arsenal");
        }

        @Test
        @DisplayName("validates exact match from database")
        void validatesExactMatch() {
            when(matchRepository.findAllDistinctTeamNames())
                    .thenReturn(List.of("Arsenal", "Chelsea", "Liverpool"));

            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("Chelsea");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getNormalizedName()).isEqualTo("Chelsea");
        }

        @Test
        @DisplayName("returns suggestions for unknown team")
        void returnsSuggestionsForUnknownTeam() {
            when(matchRepository.findAllDistinctTeamNames())
                    .thenReturn(List.of("Arsenal", "Chelsea", "Liverpool", "Man City"));

            TeamValidationService.ValidationResult result =
                    teamValidationService.validateTeam("Arsena");  // Typo

            assertThat(result.isValid()).isFalse();
            // Should suggest Arsenal due to similarity
            assertThat(result.getSuggestions()).contains("Arsenal");
        }
    }

    @Nested
    @DisplayName("getValidTeams()")
    class GetValidTeamsTests {

        @Test
        @DisplayName("returns all teams from repository")
        void returnsAllTeams() {
            when(matchRepository.findAllDistinctTeamNames())
                    .thenReturn(List.of("Arsenal", "Chelsea", "Liverpool"));

            var teams = teamValidationService.getValidTeams();

            assertThat(teams).containsExactlyInAnyOrder("Arsenal", "Chelsea", "Liverpool");
        }

        @Test
        @DisplayName("returns sorted set")
        void returnsSortedSet() {
            when(matchRepository.findAllDistinctTeamNames())
                    .thenReturn(List.of("Liverpool", "Arsenal", "Chelsea"));

            var teams = teamValidationService.getValidTeams();

            // TreeSet should sort alphabetically
            assertThat(teams.stream().toList())
                    .containsExactly("Arsenal", "Chelsea", "Liverpool");
        }
    }
}

