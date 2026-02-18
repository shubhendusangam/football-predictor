package com.app.footballprediction.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO for football-data.org API responses.
 * Maps the matches endpoint response structure.
 *
 * @see <a href="https://www.football-data.org/documentation/api">API Documentation</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FootballApiResponse {

    private ResultSet resultSet;
    private Competition competition;
    private List<ApiMatch> matches;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResultSet {
        private Integer count;
        private String first;
        private String last;
        private Integer played;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Competition {
        private Long id;
        private String name;
        private String code;
        private String type;
        private String emblem;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiMatch {
        private Long id;
        private String utcDate;
        private String status;  // SCHEDULED, TIMED, IN_PLAY, PAUSED, FINISHED, POSTPONED, CANCELLED
        private Integer matchday;
        private String stage;
        private TeamInfo homeTeam;
        private TeamInfo awayTeam;
        private Score score;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String shortName;
        private String tla;  // Three-letter abbreviation
        private String crest;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Score {
        private String winner;  // HOME_TEAM, AWAY_TEAM, DRAW
        private String duration;  // REGULAR, EXTRA_TIME, PENALTY_SHOOTOUT
        private ScoreDetail fullTime;
        private ScoreDetail halfTime;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetail {
        private Integer home;
        private Integer away;
    }
}

