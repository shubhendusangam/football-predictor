package com.app.footballprediction.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * DTO for football-data.org standings endpoint response.
 * Used to get current season form for teams.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StandingsResponse {

    private Competition competition;
    private Season season;
    private List<StandingType> standings;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Competition {
        private Long id;
        private String name;
        private String code;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Season {
        private Long id;
        private String startDate;
        private String endDate;
        private Integer currentMatchday;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StandingType {
        private String stage;
        private String type;  // TOTAL, HOME, AWAY
        private List<TableEntry> table;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableEntry {
        private Integer position;
        private TeamInfo team;
        private Integer playedGames;
        private String form;  // e.g., "W,W,D,L,W" - last 5 matches
        private Integer won;
        private Integer draw;
        private Integer lost;
        private Integer points;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer goalDifference;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String shortName;
        private String tla;
        private String crest;
    }
}

