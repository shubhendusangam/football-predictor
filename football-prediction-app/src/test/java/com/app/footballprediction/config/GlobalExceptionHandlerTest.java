package com.app.footballprediction.config;

import com.app.footballprediction.exception.DataSyncException;
import com.app.footballprediction.exception.ErrorCode;
import com.app.footballprediction.exception.ModelNotReadyException;
import com.app.footballprediction.exception.ResourceNotFoundException;
import com.app.footballprediction.exception.TeamNotFoundException;
import com.app.footballprediction.exception.ValidationException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * <p>
 * Uses a standalone MockMvc with a minimal stub controller whose endpoints
 * deliberately throw each exception type so we can assert the RFC 7807
 * ProblemDetail response body.
 */
@DisplayName("GlobalExceptionHandler – RFC 7807 ProblemDetail Tests")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new StubController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── Stub controller used only by this test ─────────────────────────

    @RestController
    static class StubController {

        record ValidationBody(@NotBlank String homeTeam, @NotBlank String awayTeam) {}

        @GetMapping("/test/resource-not-found")
        String resourceNotFound() {
            throw new ResourceNotFoundException("Prediction", "42");
        }

        @GetMapping("/test/resource-not-found-custom-code")
        String resourceNotFoundCustomCode() {
            throw new ResourceNotFoundException("Prediction", "42", ErrorCode.PREDICTION_NOT_FOUND);
        }

        @GetMapping("/test/validation-exception")
        String validationException() {
            throw new ValidationException("Team name is invalid",
                    Map.of("homeTeam", "must not be blank"));
        }

        @GetMapping("/test/validation-exception-custom-code")
        String validationExceptionCustomCode() {
            throw new ValidationException(ErrorCode.INVALID_TEAM_NAME,
                    "Invalid team name supplied",
                    Map.of("homeTeam", "Unknown team 'FooFC'"));
        }

        @PostMapping("/test/method-argument-not-valid")
        String methodArgumentNotValid(@Valid @RequestBody ValidationBody body) {
            return "ok";
        }

        @GetMapping("/test/team-not-found")
        String teamNotFound() {
            throw new TeamNotFoundException("FooFC");
        }

        @GetMapping("/test/model-not-ready")
        String modelNotReady() {
            throw new ModelNotReadyException();
        }

        @GetMapping("/test/data-sync")
        String dataSyncFailed() {
            throw new DataSyncException("API returned 503");
        }

        @GetMapping("/test/illegal-argument")
        String illegalArgument() {
            throw new IllegalArgumentException("Season must be in format YYYY/YY");
        }

        @GetMapping("/test/illegal-state")
        String illegalState() {
            throw new IllegalStateException("Service is still initializing");
        }

        @GetMapping("/test/null-pointer")
        String nullPointer() {
            throw new NullPointerException("oops");
        }

        @GetMapping("/test/generic-exception")
        String genericException() throws Exception {
            throw new Exception("Something totally unexpected");
        }

        @GetMapping("/test/requires-param")
        String requiresParam(@RequestParam String season) {
            return season;
        }
    }

    // ─── 404 – ResourceNotFoundException ────────────────────────────────

    @Nested
    @DisplayName("ResourceNotFoundException (404)")
    class ResourceNotFoundTests {

        @Test
        @DisplayName("returns 404 with default RESOURCE_NOT_FOUND error code")
        void returnsNotFoundWithDefaultCode() throws Exception {
            mockMvc.perform(get("/test/resource-not-found")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.title").value("Not Found"))
                    .andExpect(jsonPath("$.detail").value("Prediction not found: 42"))
                    .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.resourceType").value("Prediction"))
                    .andExpect(jsonPath("$.identifier").value("42"))
                    .andExpect(jsonPath("$.type").value("/errors/resource-not-found"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("returns 404 with custom PREDICTION_NOT_FOUND error code")
        void returnsNotFoundWithCustomCode() throws Exception {
            mockMvc.perform(get("/test/resource-not-found-custom-code")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PREDICTION_NOT_FOUND"))
                    .andExpect(jsonPath("$.type").value("/errors/prediction-not-found"));
        }
    }

    // ─── 400 – ValidationException ──────────────────────────────────────

    @Nested
    @DisplayName("ValidationException (400)")
    class ValidationExceptionTests {

        @Test
        @DisplayName("returns 400 with field errors map")
        void returnsBadRequestWithFieldErrors() throws Exception {
            mockMvc.perform(get("/test/validation-exception")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.title").value("Bad Request"))
                    .andExpect(jsonPath("$.detail").value("Team name is invalid"))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.fieldErrors.homeTeam").value("must not be blank"))
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @DisplayName("returns 400 with custom INVALID_TEAM_NAME code")
        void returnsBadRequestWithCustomCode() throws Exception {
            mockMvc.perform(get("/test/validation-exception-custom-code")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TEAM_NAME"))
                    .andExpect(jsonPath("$.type").value("/errors/invalid-team-name"))
                    .andExpect(jsonPath("$.fieldErrors.homeTeam").value("Unknown team 'FooFC'"));
        }
    }

    // ─── 400 – MethodArgumentNotValidException ──────────────────────────

    @Nested
    @DisplayName("MethodArgumentNotValidException (400)")
    class MethodArgumentNotValidTests {

        @Test
        @DisplayName("returns 400 with VALIDATION_FAILED and field errors from @Valid")
        void returnsBadRequestForBeanValidation() throws Exception {
            mockMvc.perform(post("/test/method-argument-not-valid")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"homeTeam\":\"\", \"awayTeam\":\"\"}")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.detail").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors").isMap())
                    .andExpect(jsonPath("$.fieldErrors.homeTeam").exists())
                    .andExpect(jsonPath("$.fieldErrors.awayTeam").exists());
        }
    }

    // ─── 400 – TeamNotFoundException ────────────────────────────────────

    @Nested
    @DisplayName("TeamNotFoundException (400)")
    class TeamNotFoundTests {

        @Test
        @DisplayName("returns 400 with INVALID_TEAM_NAME error code")
        void returnsBadRequestForUnknownTeam() throws Exception {
            mockMvc.perform(get("/test/team-not-found")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_TEAM_NAME"))
                    .andExpect(jsonPath("$.detail").value("Team not found: FooFC"))
                    .andExpect(jsonPath("$.hint").value("Use GET /api/teams to see valid team names"));
        }
    }

    // ─── 503 – ModelNotReadyException ───────────────────────────────────

    @Nested
    @DisplayName("ModelNotReadyException (503)")
    class ModelNotReadyTests {

        @Test
        @DisplayName("returns 503 with MODEL_NOT_TRAINED error code")
        void returnsServiceUnavailable() throws Exception {
            mockMvc.perform(get("/test/model-not-ready")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value(503))
                    .andExpect(jsonPath("$.errorCode").value("MODEL_NOT_TRAINED"))
                    .andExpect(jsonPath("$.hint").value("Call POST /api/model/train first"));
        }
    }

    // ─── 500 – DataSyncException ────────────────────────────────────────

    @Nested
    @DisplayName("DataSyncException (500)")
    class DataSyncTests {

        @Test
        @DisplayName("returns 500 with DATA_SYNC_FAILED error code")
        void returnsInternalServerError() throws Exception {
            mockMvc.perform(get("/test/data-sync")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("DATA_SYNC_FAILED"))
                    .andExpect(jsonPath("$.detail").value("API returned 503"));
        }
    }

    // ─── 400 – IllegalArgumentException ─────────────────────────────────

    @Nested
    @DisplayName("IllegalArgumentException (400)")
    class IllegalArgumentTests {

        @Test
        @DisplayName("returns 400 with INVALID_REQUEST error code")
        void returnsBadRequest() throws Exception {
            mockMvc.perform(get("/test/illegal-argument")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.detail").value("Season must be in format YYYY/YY"));
        }
    }

    // ─── 503 – IllegalStateException ────────────────────────────────────

    @Nested
    @DisplayName("IllegalStateException (503)")
    class IllegalStateTests {

        @Test
        @DisplayName("returns 503 with hint about initializing")
        void returnsServiceUnavailable() throws Exception {
            mockMvc.perform(get("/test/illegal-state")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.hint").value("The system may still be initializing. Please try again."));
        }
    }

    // ─── 500 – NullPointerException ─────────────────────────────────────

    @Nested
    @DisplayName("NullPointerException (500)")
    class NullPointerTests {

        @Test
        @DisplayName("returns 500 with INTERNAL_ERROR – no stack trace leaked")
        void returnsInternalServerError() throws Exception {
            mockMvc.perform(get("/test/null-pointer")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("An unexpected error occurred while processing your request"))
                    .andExpect(jsonPath("$.hint").exists());
        }
    }

    // ─── 500 – Generic Exception (catch-all) ────────────────────────────

    @Nested
    @DisplayName("Generic Exception catch-all (500)")
    class GenericExceptionTests {

        @Test
        @DisplayName("returns 500 with INTERNAL_ERROR and exception type")
        void returnsInternalServerError() throws Exception {
            mockMvc.perform(get("/test/generic-exception")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.title").value("Internal Server Error"))
                    .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.exceptionType").value("Exception"))
                    .andExpect(jsonPath("$.hint").value("Please check the logs for more details"));
        }
    }

    // ─── 400 – MissingServletRequestParameterException ──────────────────

    @Nested
    @DisplayName("MissingServletRequestParameterException (400)")
    class MissingParamTests {

        @Test
        @DisplayName("returns 400 when required query parameter is missing")
        void returnsBadRequestForMissingParam() throws Exception {
            mockMvc.perform(get("/test/requires-param")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                    .andExpect(jsonPath("$.detail").value("Required parameter 'season' is missing"));
        }
    }
}

