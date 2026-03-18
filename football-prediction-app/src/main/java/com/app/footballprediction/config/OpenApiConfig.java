package com.app.footballprediction.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger UI configuration.
 *
 * <p>Defines the global API metadata (title, version, description, contact)
 * and groups endpoints into logical modules that appear as separate drop-downs
 * in the Swagger UI.</p>
 *
 * <p>Swagger UI is available at {@code /swagger-ui.html} and the raw
 * OpenAPI JSON spec at {@code /v3/api-docs}.</p>
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI footballPredictionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Football Prediction API")
                        .version("1.0.0")
                        .description("""
                                Premier League match prediction engine powered by Machine Learning.
                                
                                Provides match outcome predictions, team analytics, head-to-head insights,
                                league standings, expected goals (xG), corner statistics, and more.
                                
                                **Authentication:** Admin endpoints require HTTP Basic Auth.
                                Public endpoints (predictions, analytics, teams) are open access.
                                """)
                        .contact(new Contact()
                                .name("Football Prediction Team")
                                .url("https://github.com/football-prediction")
                                .email("support@football-prediction.app"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local development server")))
                .components(new Components()
                        .addSecuritySchemes("basicAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic Authentication for admin endpoints")));
    }

    // ── API Groups ──────────────────────────────────────────────────────

    @Bean
    public GroupedOpenApi predictionsGroup() {
        return GroupedOpenApi.builder()
                .group("predictions")
                .displayName("Predictions")
                .pathsToMatch(
                        "/api/predict",
                        "/api/predictions/**",
                        "/api/h2h",
                        "/api/insights/**",
                        "/api/external/**")
                .build();
    }

    @Bean
    public GroupedOpenApi analyticsGroup() {
        return GroupedOpenApi.builder()
                .group("analytics")
                .displayName("Analytics")
                .pathsToMatch(
                        "/api/analytics/**",
                        "/api/league/**",
                        "/api/matches/predict-cards",
                        "/api/matches/predict-corners",
                        "/api/matches/predict-xg",
                        "/api/matches/congestion-comparison",
                        "/api/teams/*/form-guide",
                        "/api/teams/*/half-analysis",
                        "/api/teams/*/kickoff-analysis",
                        "/api/teams/*/corner-stats",
                        "/api/teams/*/expected-goals",
                        "/api/teams/*/expected-goals/split",
                        "/api/teams/*/fixture-congestion",
                        "/api/teams/*/discipline")
                .build();
    }

    @Bean
    public GroupedOpenApi teamsGroup() {
        return GroupedOpenApi.builder()
                .group("teams")
                .displayName("Teams")
                .pathsToMatch(
                        "/api/teams",
                        "/api/teams/seasons",
                        "/api/teams/form",
                        "/api/teams/logo",
                        "/api/teams/logos",
                        "/api/teams/logo-status",
                        "/api/teams/*/stats",
                        "/api/teams/*/analytics",
                        "/api/referees/**",
                        "/api/seasons/**",
                        "/api/season/**",
                        "/api/news/**")
                .build();
    }

    @Bean
    public GroupedOpenApi healthGroup() {
        return GroupedOpenApi.builder()
                .group("health")
                .displayName("Health & Admin")
                .pathsToMatch(
                        "/api/model/**",
                        "/api/dashboard/**",
                        "/api/admin/**",
                        "/api/cache/**",
                        "/api/data/**",
                        "/api/matches/history",
                        "/api/matches/upcoming",
                        "/api/matches/{id}")
                .build();
    }
}

