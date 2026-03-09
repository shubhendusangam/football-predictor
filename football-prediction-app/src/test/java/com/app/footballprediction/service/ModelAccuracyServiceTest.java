package com.app.footballprediction.service;

import com.app.common.model.ModelAccuracy;
import com.app.common.model.PredictionEvaluation;
import com.app.common.repository.ModelAccuracyRepository;
import com.app.common.repository.PredictionEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ModelAccuracyService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelAccuracyService Unit Tests")
class ModelAccuracyServiceTest {

    @Mock
    private PredictionEvaluationRepository evaluationRepository;

    @Mock
    private ModelAccuracyRepository accuracyRepository;

    @InjectMocks
    private ModelAccuracyService modelAccuracyService;

    @Nested
    @DisplayName("recalculateGlobalAccuracy()")
    class RecalculateGlobalAccuracyTests {

        @Test
        @DisplayName("returns null when no evaluations exist")
        void returnsNullForNoEvaluations() {
            when(evaluationRepository.countAllEvaluations()).thenReturn(0L);

            ModelAccuracy result = modelAccuracyService.recalculateGlobalAccuracy();

            assertThat(result).isNull();
            verify(accuracyRepository, never()).save(any());
        }

        @Test
        @DisplayName("calculates correct winner accuracy")
        void calculatesWinnerAccuracy() {
            when(evaluationRepository.countAllEvaluations()).thenReturn(10L);
            when(evaluationRepository.countCorrectWinnerPredictions()).thenReturn(7L);
            when(evaluationRepository.countExactScorePredictions()).thenReturn(2L);
            when(evaluationRepository.getAverageGoalDifferenceError()).thenReturn(1.1);
            when(evaluationRepository.getAverageCardPredictionError()).thenReturn(1.5);
            when(evaluationRepository.getAverageCornerPredictionError()).thenReturn(2.3);
            when(accuracyRepository.save(any(ModelAccuracy.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ModelAccuracy result = modelAccuracyService.recalculateGlobalAccuracy();

            assertThat(result).isNotNull();
            assertThat(result.getScope()).isEqualTo("GLOBAL");
            assertThat(result.getTotalPredictions()).isEqualTo(10L);
            assertThat(result.getCorrectWinnerPredictions()).isEqualTo(7L);
            assertThat(result.getWinnerAccuracy()).isEqualTo(0.7);
            assertThat(result.getScoreAccuracy()).isEqualTo(0.2);
            assertThat(result.getGoalErrorAverage()).isEqualTo(1.1);
        }

        @Test
        @DisplayName("calculates 100% accuracy when all predictions correct")
        void calculatesPerfectAccuracy() {
            when(evaluationRepository.countAllEvaluations()).thenReturn(5L);
            when(evaluationRepository.countCorrectWinnerPredictions()).thenReturn(5L);
            when(evaluationRepository.countExactScorePredictions()).thenReturn(5L);
            when(evaluationRepository.getAverageGoalDifferenceError()).thenReturn(0.0);
            when(evaluationRepository.getAverageCardPredictionError()).thenReturn(0.0);
            when(evaluationRepository.getAverageCornerPredictionError()).thenReturn(0.0);
            when(accuracyRepository.save(any(ModelAccuracy.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            ModelAccuracy result = modelAccuracyService.recalculateGlobalAccuracy();

            assertThat(result.getWinnerAccuracy()).isEqualTo(1.0);
            assertThat(result.getScoreAccuracy()).isEqualTo(1.0);
            assertThat(result.getGoalErrorAverage()).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("recalculatePerTeamAccuracy()")
    class RecalculatePerTeamAccuracyTests {

        @Test
        @DisplayName("groups evaluations by team correctly")
        void groupsByTeam() {
            PredictionEvaluation eval1 = PredictionEvaluation.builder()
                    .matchId(1L)
                    .homeTeam("Arsenal")
                    .awayTeam("Chelsea")
                    .winnerCorrect(true)
                    .scoreExact(false)
                    .goalDifferenceError(1)
                    .evaluationTime(LocalDateTime.now())
                    .build();

            PredictionEvaluation eval2 = PredictionEvaluation.builder()
                    .matchId(2L)
                    .homeTeam("Arsenal")
                    .awayTeam("Liverpool")
                    .winnerCorrect(false)
                    .scoreExact(false)
                    .goalDifferenceError(2)
                    .evaluationTime(LocalDateTime.now())
                    .build();

            when(evaluationRepository.findAll()).thenReturn(List.of(eval1, eval2));
            when(accuracyRepository.save(any(ModelAccuracy.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<ModelAccuracy> results = modelAccuracyService.recalculatePerTeamAccuracy();

            // Should create accuracy entries for Arsenal (2 evals), Chelsea (1 eval), Liverpool (1 eval)
            assertThat(results).hasSize(3);

            // Verify Arsenal has 2 predictions
            ArgumentCaptor<ModelAccuracy> captor = ArgumentCaptor.forClass(ModelAccuracy.class);
            verify(accuracyRepository, times(3)).save(captor.capture());

            Optional<ModelAccuracy> arsenalAccuracy = captor.getAllValues().stream()
                    .filter(a -> "Arsenal".equals(a.getScopeKey()))
                    .findFirst();
            assertThat(arsenalAccuracy).isPresent();
            assertThat(arsenalAccuracy.get().getTotalPredictions()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("getErrorAnalysis()")
    class GetErrorAnalysisTests {

        @Test
        @DisplayName("returns message when no evaluations exist")
        void returnsMessageForNoData() {
            when(evaluationRepository.countAllEvaluations()).thenReturn(0L);

            Map<String, Object> analysis = modelAccuracyService.getErrorAnalysis();

            assertThat(analysis).containsKey("message");
            assertThat(analysis.get("totalEvaluations")).isEqualTo(0L);
        }

        @Test
        @DisplayName("returns complete analysis with data")
        void returnsCompleteAnalysis() {
            when(evaluationRepository.countAllEvaluations()).thenReturn(20L);
            when(evaluationRepository.countCorrectWinnerPredictions()).thenReturn(14L);
            when(evaluationRepository.countExactScorePredictions()).thenReturn(3L);
            when(evaluationRepository.getAverageGoalDifferenceError()).thenReturn(1.2);
            when(evaluationRepository.getAverageCardPredictionError()).thenReturn(1.8);
            when(evaluationRepository.getAverageCornerPredictionError()).thenReturn(2.5);
            when(evaluationRepository.findDistinctSeasons()).thenReturn(List.of("2025-26"));
            when(evaluationRepository.countBySeason("2025-26")).thenReturn(20L);
            when(evaluationRepository.countCorrectWinnerBySeason("2025-26")).thenReturn(14L);
            when(evaluationRepository.getAverageGoalErrorBySeason("2025-26")).thenReturn(1.2);

            Map<String, Object> analysis = modelAccuracyService.getErrorAnalysis();

            assertThat(analysis.get("totalEvaluations")).isEqualTo(20L);
            assertThat(analysis.get("winnerAccuracy")).isEqualTo("70.0%");
            assertThat(analysis.get("scoreAccuracy")).isEqualTo("15.0%");
            assertThat(analysis).containsKey("seasonBreakdown");
        }
    }
}

