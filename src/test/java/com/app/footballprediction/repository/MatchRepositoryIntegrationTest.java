package com.app.footballprediction.repository;

import com.app.footballprediction.model.Match;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for MatchRepository.
 * Uses embedded H2 database.
 */

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("MatchRepository Integration Tests")
class MatchRepositoryIntegrationTest {

    @Autowired
    private MatchRepository matchRepository;

    @BeforeEach
    void setUp() {
        matchRepository.deleteAll();
    }

    @Nested
    @DisplayName("Basic CRUD operations")
    class CrudTests {

        @Test
        @DisplayName("saves and retrieves a match")
        void savesAndRetrievesMatch() {
            Match match = Match.builder()
                    .matchDate(LocalDate.of(2024, 1, 15))
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .fullTimeHomeGoals(2)
                    .fullTimeAwayGoals(1)
                    .fullTimeResult("H")
                    .build();

            Match saved = matchRepository.save(match);

            assertThat(saved.getId()).isNotNull();
            assertThat(matchRepository.findById(saved.getId())).isPresent();
        }

        @Test
        @DisplayName("counts matches correctly")
        void countsMatches() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-15"));
            matchRepository.save(createMatch("Liverpool", "Man United", "2024-01-16"));

            assertThat(matchRepository.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("findByTeamBeforeDate()")
    class FindByTeamBeforeDateTests {

        @Test
        @DisplayName("finds all matches for a team before date")
        void findsMatchesBeforeDate() {
            // Arsenal matches
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-10"));
            matchRepository.save(createMatch("Liverpool", "Arsenal", "2024-01-12"));
            matchRepository.save(createMatch("Arsenal", "Tottenham", "2024-01-20")); // After cutoff

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> matches = matchRepository.findByTeamBeforeDate("Arsenal", cutoff);

            assertThat(matches).hasSize(2);
            assertThat(matches).allMatch(m ->
                    m.getMatchDate().isBefore(cutoff));
        }

        @Test
        @DisplayName("returns matches ordered by date descending")
        void returnsInDescendingOrder() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-05"));
            matchRepository.save(createMatch("Arsenal", "Liverpool", "2024-01-10"));
            matchRepository.save(createMatch("Arsenal", "Tottenham", "2024-01-08"));

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> matches = matchRepository.findByTeamBeforeDate("Arsenal", cutoff);

            assertThat(matches).hasSize(3);
            assertThat(matches.get(0).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 10));
            assertThat(matches.get(1).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 8));
            assertThat(matches.get(2).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 5));
        }

        @Test
        @DisplayName("returns empty list for unknown team")
        void returnsEmptyForUnknownTeam() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-10"));

            List<Match> matches = matchRepository.findByTeamBeforeDate("Unknown",
                    LocalDate.of(2024, 1, 15));

            assertThat(matches).isEmpty();
        }
    }

    @Nested
    @DisplayName("findHomeMatchesByTeamBeforeDate()")
    class FindHomeMatchesTests {

        @Test
        @DisplayName("finds only home matches")
        void findsOnlyHomeMatches() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-10")); // Home
            matchRepository.save(createMatch("Liverpool", "Arsenal", "2024-01-12")); // Away

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> matches = matchRepository.findHomeMatchesByTeamBeforeDate("Arsenal", cutoff);

            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).getHomeTeam()).isEqualTo("Arsenal");
        }
    }

    @Nested
    @DisplayName("findAwayMatchesByTeamBeforeDate()")
    class FindAwayMatchesTests {

        @Test
        @DisplayName("finds only away matches")
        void findsOnlyAwayMatches() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-10")); // Home
            matchRepository.save(createMatch("Liverpool", "Arsenal", "2024-01-12")); // Away

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> matches = matchRepository.findAwayMatchesByTeamBeforeDate("Arsenal", cutoff);

            assertThat(matches).hasSize(1);
            assertThat(matches.get(0).getAwayTeam()).isEqualTo("Arsenal");
        }
    }

    @Nested
    @DisplayName("findH2HBeforeDate()")
    class FindH2HTests {

        @Test
        @DisplayName("finds head-to-head matches in both directions")
        void findsH2HBothDirections() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-05"));
            matchRepository.save(createMatch("Chelsea", "Arsenal", "2024-01-10"));
            matchRepository.save(createMatch("Arsenal", "Liverpool", "2024-01-08")); // Different opponent

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> h2h = matchRepository.findH2HBeforeDate("Arsenal", "Chelsea", cutoff);

            assertThat(h2h).hasSize(2);
        }

        @Test
        @DisplayName("returns empty when no H2H history")
        void returnsEmptyWhenNoH2H() {
            matchRepository.save(createMatch("Arsenal", "Liverpool", "2024-01-08"));

            LocalDate cutoff = LocalDate.of(2024, 1, 15);
            List<Match> h2h = matchRepository.findH2HBeforeDate("Arsenal", "Chelsea", cutoff);

            assertThat(h2h).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByOrderByMatchDateAsc()")
    class FindAllOrderedTests {

        @Test
        @DisplayName("returns all matches in ascending date order")
        void returnsInAscendingOrder() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-15"));
            matchRepository.save(createMatch("Liverpool", "Man United", "2024-01-10"));
            matchRepository.save(createMatch("Tottenham", "Man City", "2024-01-20"));

            List<Match> matches = matchRepository.findAllByOrderByMatchDateAsc();

            assertThat(matches).hasSize(3);
            assertThat(matches.get(0).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 10));
            assertThat(matches.get(1).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 15));
            assertThat(matches.get(2).getMatchDate()).isEqualTo(LocalDate.of(2024, 1, 20));
        }
    }

    @Nested
    @DisplayName("existsByMatchDateAndHomeTeamAndAwayTeam()")
    class ExistsDuplicateTests {

        @Test
        @DisplayName("returns true for existing match")
        void returnsTrueForExistingMatch() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-15"));

            boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                    LocalDate.of(2024, 1, 15),
                    "Arsenal",
                    "Chelsea"
            );

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existing match")
        void returnsFalseForNonExisting() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-15"));

            boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                    LocalDate.of(2024, 1, 15),
                    "Arsenal",
                    "Liverpool" // Different opponent
            );

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns false for same teams on different date")
        void returnsFalseForDifferentDate() {
            matchRepository.save(createMatch("Arsenal", "Chelsea", "2024-01-15"));

            boolean exists = matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                    LocalDate.of(2024, 1, 16), // Different date
                    "Arsenal",
                    "Chelsea"
            );

            assertThat(exists).isFalse();
        }
    }

    // ── Helper methods ──────────────────────────────────────────────────

    private Match createMatch(String home, String away, String dateStr) {
        return Match.builder()
                .matchDate(LocalDate.parse(dateStr))
                .homeTeam(home)
                .awayTeam(away)
                .fullTimeHomeGoals(2)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .build();
    }
}

