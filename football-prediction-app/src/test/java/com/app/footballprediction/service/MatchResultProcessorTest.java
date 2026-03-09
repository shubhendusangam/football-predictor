package com.app.footballprediction.service;

import com.app.common.model.Match;
import com.app.common.model.Prediction;
import com.app.common.model.PredictionEvaluation;
import com.app.common.repository.MatchRepository;
import com.app.common.repository.PredictionEvaluationRepository;
import com.app.common.repository.PredictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MatchResultProcessor.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MatchResultProcessor Unit Tests")
class MatchResultProcessorTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private PredictionRepository predictionRepository;

    @Mock
    private PredictionEvaluationRepository evaluationRepository;

    @InjectMocks
    private MatchResultProcessor matchResultProcessor;

    private Match sampleMatch;
    private Prediction homePrediction;
    private Prediction awayPrediction;

    @BeforeEach
    void setUp() {
        sampleMatch = Match.builder()
                .id(100L)  // local DB ID
                .homeTeam("Arsenal")
                .awayTeam("Chelsea")
                .matchDate(LocalDate.of(2026, 3, 1))
                .fullTimeHomeGoals(3)
                .fullTimeAwayGoals(1)
                .fullTimeResult("H")
                .season("2025-26")
                .homeYellowCards(2)
                .awayYellowCards(3)
                .homeRedCards(0)
                .awayRedCards(0)
                .homeCorners(7)
                .awayCorners(4)
                .build();

        homePrediction = Prediction.builder()
                .id(1L)
                .matchId(999L)  // external API ID — different from match.id!
                .teamName("Arsenal")
                .opponentName("Chelsea")
                .isHome(true)
                .season("2025-26")
                .matchDate(LocalDate.of(2026, 3, 1))
                .predictedResult("WIN")
                .predictedHomeGoals(2)
                .predictedAwayGoals(1)
                .confidence(0.7)
                .predictionDate(LocalDateTime.now())
                .build();

        awayPrediction = Prediction.builder()
                .id(2L)
                .matchId(999L)
                .teamName("Chelsea")
                .opponentName("Arsenal")
                .isHome(false)
                .season("2025-26")
                .matchDate(LocalDate.of(2026, 3, 1))
                .predictedResult("LOSS")
                .predictedHomeGoals(2)
                .predictedAwayGoals(1)
                .confidence(0.7)
                .predictionDate(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("processAllUnresolvedPredictions()")
    class ProcessAllUnresolvedTests {

        @Test
        @DisplayName("returns 0 when no unresolved predictions exist")
        void returnsZeroWhenNoPredictions() {
            when(predictionRepository.findAllUnresolvedPredictionsBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());

            int result = matchResultProcessor.processAllUnresolvedPredictions();

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("resolves prediction when corresponding match found by team+date")
        void resolvesPredictionByTeamAndDate() {
            when(predictionRepository.findAllUnresolvedPredictionsBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(homePrediction));
            // Pre-loaded match map contains the finished match
            when(matchRepository.findAllFinishedMatchesBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(sampleMatch));
            when(evaluationRepository.findAll()).thenReturn(List.of());
            when(predictionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(evaluationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = matchResultProcessor.processAllUnresolvedPredictions();

            assertThat(result).isEqualTo(1);
            // Prediction should be resolved
            assertThat(homePrediction.getActualResult()).isEqualTo("WIN");
            assertThat(homePrediction.getIsCorrect()).isTrue();
        }

        @Test
        @DisplayName("resolves prediction when team is playing away (reverse lookup)")
        void resolvesPredictionAsAwayTeam() {
            when(predictionRepository.findAllUnresolvedPredictionsBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(awayPrediction));
            // Pre-loaded match map contains the finished match (lookup tries both orientations)
            when(matchRepository.findAllFinishedMatchesBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(sampleMatch));
            when(evaluationRepository.findAll()).thenReturn(List.of());
            when(predictionRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
            when(evaluationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            int result = matchResultProcessor.processAllUnresolvedPredictions();

            assertThat(result).isEqualTo(1);
            // Away team lost (result was H)
            assertThat(awayPrediction.getActualResult()).isEqualTo("LOSS");
            assertThat(awayPrediction.getIsCorrect()).isTrue(); // predicted LOSS, actual LOSS
        }

        @Test
        @DisplayName("skips prediction when match has no result yet")
        void skipsPredictionWhenMatchNotFinished() {
            when(predictionRepository.findAllUnresolvedPredictionsBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of(homePrediction));
            // No finished matches in the pre-loaded map
            when(matchRepository.findAllFinishedMatchesBeforeDate(any(LocalDate.class)))
                    .thenReturn(List.of());
            when(evaluationRepository.findAll()).thenReturn(List.of());

            int result = matchResultProcessor.processAllUnresolvedPredictions();

            assertThat(result).isEqualTo(0);
            verify(evaluationRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("findNewFinishedMatchesForDate()")
    class FindNewFinishedMatchesForDateTests {

        @Test
        @DisplayName("returns empty list when no finished matches on date")
        void returnsEmptyForNoMatches() {
            when(matchRepository.findFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(List.of());

            List<Match> result = matchResultProcessor.findNewFinishedMatchesForDate(LocalDate.now());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("filters out already-evaluated matches")
        void filtersAlreadyEvaluated() {
            Match evaluated = Match.builder().id(10L).homeTeam("A").awayTeam("B")
                    .fullTimeResult("H").build();
            Match notEvaluated = Match.builder().id(11L).homeTeam("C").awayTeam("D")
                    .fullTimeResult("D").build();

            when(matchRepository.findFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(List.of(evaluated, notEvaluated));
            when(evaluationRepository.existsByMatchId(10L)).thenReturn(true);
            when(evaluationRepository.existsByMatchId(11L)).thenReturn(false);

            List<Match> result = matchResultProcessor.findNewFinishedMatchesForDate(LocalDate.now());

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(11L);
        }
    }

    @Nested
    @DisplayName("processFinishedMatchesForDate()")
    class ProcessFinishedMatchesForDateTests {

        @Test
        @DisplayName("returns 0 when no new finished matches on date")
        void returnsZeroForDate() {
            when(matchRepository.findFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(List.of());

            int result = matchResultProcessor.processFinishedMatchesForDate(LocalDate.now());

            assertThat(result).isEqualTo(0);
        }

        @Test
        @DisplayName("evaluates match and resolves predictions found by team+date")
        void evaluatesAndResolves() {
            when(matchRepository.findFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(List.of(sampleMatch));
            when(evaluationRepository.existsByMatchId(100L)).thenReturn(false);
            // findAndResolve for home team
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc("Arsenal"))
                    .thenReturn(List.of(homePrediction));
            // findAndResolve for away team
            when(predictionRepository.findByTeamNameIgnoreCaseOrderByMatchDateDesc("Chelsea"))
                    .thenReturn(List.of(awayPrediction));
            when(evaluationRepository.save(any(PredictionEvaluation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(predictionRepository.save(any(Prediction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int result = matchResultProcessor.processFinishedMatchesForDate(LocalDate.of(2026, 3, 1));

            assertThat(result).isEqualTo(1);
            // Both predictions should be resolved
            assertThat(homePrediction.getActualResult()).isEqualTo("WIN");
            assertThat(awayPrediction.getActualResult()).isEqualTo("LOSS");
        }
    }

    @Nested
    @DisplayName("hasLiveOrPendingMatches()")
    class HasLiveOrPendingMatchesTests {

        @Test
        @DisplayName("returns true when unfinished matches exist")
        void returnsTrueWhenUnfinished() {
            when(matchRepository.countUnfinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(3L);
            assertThat(matchResultProcessor.hasLiveOrPendingMatches(LocalDate.now())).isTrue();
        }

        @Test
        @DisplayName("returns false when all matches finished")
        void returnsFalseWhenAllFinished() {
            when(matchRepository.countUnfinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(0L);
            assertThat(matchResultProcessor.hasLiveOrPendingMatches(LocalDate.now())).isFalse();
        }
    }

    @Nested
    @DisplayName("hasFinishedMatchesToday()")
    class HasFinishedMatchesTodayTests {

        @Test
        @DisplayName("returns true when finished matches exist")
        void returnsTrueWhenFinished() {
            when(matchRepository.countFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(5L);
            assertThat(matchResultProcessor.hasFinishedMatchesToday(LocalDate.now())).isTrue();
        }

        @Test
        @DisplayName("returns false when no finished matches")
        void returnsFalseWhenNoFinished() {
            when(matchRepository.countFinishedMatchesByDate(any(LocalDate.class)))
                    .thenReturn(0L);
            assertThat(matchResultProcessor.hasFinishedMatchesToday(LocalDate.now())).isFalse();
        }
    }
}

