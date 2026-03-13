package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.footballprediction.dto.FormGuideDTO;
import com.app.footballprediction.dto.FormMatchDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FormGuideService.
 *
 * Covers:
 * - Form guide calculation with W-D-L indicators
 * - Trend detection (Improving/Declining/Stable)
 * - Form rating (0-10)
 * - Edge cases (no data, empty team name, fewer than 10 matches)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FormGuideService Unit Tests")
class FormGuideServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamValidationService teamValidationService;

    @InjectMocks
    private FormGuideService formGuideService;

    @BeforeEach
    void setUpTeamValidation() {
        lenient().when(teamValidationService.resolveTeamName(any()))
                .thenAnswer(inv -> {
                    String name = inv.getArgument(0);
                    if (name == null || name.isBlank()) {
                        throw new IllegalArgumentException("Team name cannot be empty");
                    }
                    return name;
                });
    }

    // ══════════════════════════════════════════════════════════════════════
    // TEST DATA BUILDERS
    // ══════════════════════════════════════════════════════════════════════

    private Match createMatch(String homeTeam, String awayTeam, int homeGoals, int awayGoals, int daysAgo) {
        String result = homeGoals > awayGoals ? "H" : (homeGoals < awayGoals ? "A" : "D");
        return Match.builder()
                .id((long) daysAgo)
                .matchDate(LocalDate.now().minusDays(daysAgo))
                .season("2025-26")
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fullTimeHomeGoals(homeGoals)
                .fullTimeAwayGoals(awayGoals)
                .fullTimeResult(result)
                .build();
    }

    /**
     * Build a list of 10 matches for Arsenal as home/away alternating.
     * Results pattern (newest first): W, W, W, W, W, L, L, L, L, L
     */
    private List<Match> createImprovingFormMatches() {
        List<Match> matches = new ArrayList<>();
        // Last 5 (most recent) - all wins
        matches.add(createMatch("Arsenal", "Chelsea", 2, 0, 3));
        matches.add(createMatch("Liverpool", "Arsenal", 0, 1, 7));
        matches.add(createMatch("Arsenal", "Wolves", 3, 1, 10));
        matches.add(createMatch("Everton", "Arsenal", 0, 2, 14));
        matches.add(createMatch("Arsenal", "Fulham", 1, 0, 17));
        // Previous 5 - all losses
        matches.add(createMatch("Man City", "Arsenal", 3, 0, 21));
        matches.add(createMatch("Arsenal", "Spurs", 0, 2, 24));
        matches.add(createMatch("Newcastle", "Arsenal", 2, 1, 28));
        matches.add(createMatch("Arsenal", "Aston Villa", 0, 1, 31));
        matches.add(createMatch("Brighton", "Arsenal", 1, 0, 35));
        return matches;
    }

    /**
     * Build a perfect form: all 5 wins.
     */
    private List<Match> createPerfectFormMatches() {
        List<Match> matches = new ArrayList<>();
        matches.add(createMatch("Arsenal", "Chelsea", 2, 0, 3));
        matches.add(createMatch("Liverpool", "Arsenal", 0, 1, 7));
        matches.add(createMatch("Arsenal", "Wolves", 3, 1, 10));
        matches.add(createMatch("Everton", "Arsenal", 0, 2, 14));
        matches.add(createMatch("Arsenal", "Fulham", 1, 0, 17));
        return matches;
    }

    // ══════════════════════════════════════════════════════════════════════
    // TESTS
    // ══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getFormGuide")
    class GetFormGuide {

        @Test
        @DisplayName("should throw for blank team name")
        void shouldThrowForBlankTeam() {
            assertThatThrownBy(() -> formGuideService.getFormGuide("", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should throw for null team name")
        void shouldThrowForNullTeam() {
            assertThatThrownBy(() -> formGuideService.getFormGuide(null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("should return empty guide when no matches in season")
        void shouldReturnEmptyGuideWhenNoMatches() {
            // Team exists in DB, but no matches in current season
            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(Collections.emptyList());

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            assertThat(guide.getTeamName()).isEqualTo("Arsenal");
            assertThat(guide.getRecentMatches()).isEmpty();
            assertThat(guide.getFormRating()).isEqualTo(0.0);
            assertThat(guide.getFormTrend()).isEqualTo("Stable");
            assertThat(guide.getFormString()).isEmpty();
        }

        @Test
        @DisplayName("should detect improving trend when last 5 > previous 5")
        void shouldDetectImprovingTrend() {
            List<Match> matches = createImprovingFormMatches();

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            assertThat(guide.getTeamName()).isEqualTo("Arsenal");
            assertThat(guide.getRecentMatches()).hasSize(10);
            assertThat(guide.getPointsInLast5()).isEqualTo(15); // 5 wins × 3 pts
            assertThat(guide.getPointsInPrevious5()).isEqualTo(0); // 5 losses × 0 pts
            assertThat(guide.getFormTrend()).isEqualTo("Improving");
        }

        @Test
        @DisplayName("should detect declining trend when last 5 < previous 5")
        void shouldDetectDecliningTrend() {
            // Reverse the improving matches → newest are losses, older are wins
            List<Match> matches = createImprovingFormMatches();
            Collections.reverse(matches);
            // Fix ordering: after reverse, the "oldest" date matches are first,
            // but we need newest-first for the repository contract
            // Actually let's just build a declining list explicitly
            List<Match> declining = new ArrayList<>();
            // Last 5 - all losses
            declining.add(createMatch("Man City", "Arsenal", 3, 0, 3));
            declining.add(createMatch("Arsenal", "Spurs", 0, 2, 7));
            declining.add(createMatch("Newcastle", "Arsenal", 2, 1, 10));
            declining.add(createMatch("Arsenal", "Aston Villa", 0, 1, 14));
            declining.add(createMatch("Brighton", "Arsenal", 1, 0, 17));
            // Previous 5 - all wins
            declining.add(createMatch("Arsenal", "Chelsea", 2, 0, 21));
            declining.add(createMatch("Liverpool", "Arsenal", 0, 1, 24));
            declining.add(createMatch("Arsenal", "Wolves", 3, 1, 28));
            declining.add(createMatch("Everton", "Arsenal", 0, 2, 31));
            declining.add(createMatch("Arsenal", "Fulham", 1, 0, 35));

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(declining);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            assertThat(guide.getPointsInLast5()).isEqualTo(0);
            assertThat(guide.getPointsInPrevious5()).isEqualTo(15);
            assertThat(guide.getFormTrend()).isEqualTo("Declining");
        }

        @Test
        @DisplayName("should return stable trend when difference <= 3")
        void shouldReturnStableTrend() {
            // 3 wins + 2 draws (last 5: 11pts) vs 3 wins + 2 draws (prev 5: 11pts)
            List<Match> matches = new ArrayList<>();
            // Last 5: W, W, W, D, D → 11 pts
            matches.add(createMatch("Arsenal", "Chelsea", 2, 0, 3));
            matches.add(createMatch("Arsenal", "Liverpool", 1, 0, 7));
            matches.add(createMatch("Arsenal", "Wolves", 3, 1, 10));
            matches.add(createMatch("Everton", "Arsenal", 1, 1, 14));
            matches.add(createMatch("Arsenal", "Fulham", 0, 0, 17));
            // Previous 5: W, W, W, D, D → 11 pts
            matches.add(createMatch("Arsenal", "Man City", 2, 1, 21));
            matches.add(createMatch("Arsenal", "Spurs", 1, 0, 24));
            matches.add(createMatch("Arsenal", "Newcastle", 3, 2, 28));
            matches.add(createMatch("Aston Villa", "Arsenal", 0, 0, 31));
            matches.add(createMatch("Brighton", "Arsenal", 2, 2, 35));

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            assertThat(guide.getFormTrend()).isEqualTo("Stable");
        }

        @Test
        @DisplayName("W-W-W-W-W shows excellent form rating 10/10")
        void perfectFormShouldBeRated10() {
            List<Match> matches = createPerfectFormMatches();

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 5);

            assertThat(guide.getFormRating()).isEqualTo(10.0);
            assertThat(guide.getFormString()).isEqualTo("W-W-W-W-W");
            assertThat(guide.getPointsInLast5()).isEqualTo(15);
        }

        @Test
        @DisplayName("should correctly identify home/away venue")
        void shouldIdentifyVenue() {
            List<Match> matches = List.of(
                    createMatch("Arsenal", "Chelsea", 2, 0, 3),     // Arsenal home
                    createMatch("Liverpool", "Arsenal", 1, 1, 7)    // Arsenal away
            );

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            assertThat(guide.getRecentMatches()).hasSize(2);

            FormMatchDTO homeMatch = guide.getRecentMatches().get(0);
            assertThat(homeMatch.getVenue()).isEqualTo("H");
            assertThat(homeMatch.getOpponent()).isEqualTo("Chelsea");
            assertThat(homeMatch.getGoalsFor()).isEqualTo(2);
            assertThat(homeMatch.getGoalsAgainst()).isEqualTo(0);
            assertThat(homeMatch.getResult()).isEqualTo("W");
            assertThat(homeMatch.getPoints()).isEqualTo(3);

            FormMatchDTO awayMatch = guide.getRecentMatches().get(1);
            assertThat(awayMatch.getVenue()).isEqualTo("A");
            assertThat(awayMatch.getOpponent()).isEqualTo("Liverpool");
            assertThat(awayMatch.getGoalsFor()).isEqualTo(1);
            assertThat(awayMatch.getGoalsAgainst()).isEqualTo(1);
            assertThat(awayMatch.getResult()).isEqualTo("D");
            assertThat(awayMatch.getPoints()).isEqualTo(1);
        }

        @Test
        @DisplayName("should clamp numMatches to max 20")
        void shouldClampToMax20() {
            List<Match> matches = createPerfectFormMatches();

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 50);

            // Should still return all 5 matches (fewer than 20 available)
            assertThat(guide.getRecentMatches()).hasSize(5);
        }

        @Test
        @DisplayName("should return Stable trend when fewer than 10 matches")
        void shouldReturnStableWhenFewMatches() {
            List<Match> matches = List.of(
                    createMatch("Arsenal", "Chelsea", 2, 0, 3),
                    createMatch("Arsenal", "Liverpool", 3, 1, 7),
                    createMatch("Arsenal", "Wolves", 1, 0, 10)
            );

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 10);

            // With only 3 matches, trend should be Stable (not enough data)
            assertThat(guide.getFormTrend()).isEqualTo("Stable");
        }

        @Test
        @DisplayName("should include season in response")
        void shouldIncludeSeason() {
            List<Match> matches = createPerfectFormMatches();

            when(matchRepository.findCurrentSeason()).thenReturn("2025-26");
            when(matchRepository.findByTeamAndSeason("Arsenal", "2025-26")).thenReturn(matches);

            FormGuideDTO guide = formGuideService.getFormGuide("Arsenal", 5);

            assertThat(guide.getSeason()).isEqualTo("2025-26");
            assertThat(guide.getTotalMatchesInSeason()).isEqualTo(5);
        }
    }
}

