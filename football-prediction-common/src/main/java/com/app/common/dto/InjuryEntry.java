package com.app.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Single injury entry from the API-Football /injuries response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InjuryEntry {

    private InjuryPlayerInfo player;
    private InjuryTeamInfo team;
    private InjuryFixtureInfo fixture;
    private String type;   // "Hamstring", "Knee", "Suspended", "Illness" etc.
    private String reason; // "Muscle injury", "5 match ban" etc.

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjuryPlayerInfo {
        private int id;
        private String name;
        private String photo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjuryTeamInfo {
        private int id;
        private String name;
        private String logo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InjuryFixtureInfo {
        private long id;
        private String date;
    }
}

