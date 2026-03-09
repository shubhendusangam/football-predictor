package com.app.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TeamNameNormalizer.
 * Ensures API team names are correctly mapped to database format.
 */
@DisplayName("TeamNameNormalizer Tests")
class TeamNameNormalizerTest {

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("should normalize FC-suffixed team names")
    @CsvSource({
            "Manchester United FC, Man United",
            "Manchester City FC, Man City",
            "Tottenham Hotspur FC, Tottenham",
            "Newcastle United FC, Newcastle",
            "West Ham United FC, West Ham",
            "Wolverhampton Wanderers FC, Wolves",
            "Leicester City FC, Leicester",
            "Brighton & Hove Albion FC, Brighton",
            "Nottingham Forest FC, Nott'm Forest",
            "Arsenal FC, Arsenal",
            "Liverpool FC, Liverpool",
            "Chelsea FC, Chelsea",
            "Everton FC, Everton",
            "Fulham FC, Fulham",
            "Crystal Palace FC, Crystal Palace",
            "Brentford FC, Brentford",
            "Aston Villa FC, Aston Villa",
            "Ipswich Town FC, Ipswich",
            "Southampton FC, Southampton"
    })
    void normalize_fcSuffix(String apiName, String expected) {
        assertThat(TeamNameNormalizer.normalize(apiName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("should normalize AFC-suffixed team names (not leave trailing A)")
    @CsvSource({
            "AFC Bournemouth, Bournemouth",
            "Sunderland AFC, Sunderland",
            "Swansea City AFC, Swansea",
            "Huddersfield Town AFC, Huddersfield"
    })
    void normalize_afcSuffix(String apiName, String expected) {
        assertThat(TeamNameNormalizer.normalize(apiName)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @DisplayName("should normalize alternative spellings")
    @CsvSource({
            "Spurs, Tottenham",
            "Man Utd, Man United",
            "West Ham Utd, West Ham",
            "Newcastle Utd, Newcastle",
            "Sheffield Utd, Sheffield United"
    })
    void normalize_alternativeSpellings(String alias, String expected) {
        assertThat(TeamNameNormalizer.normalize(alias)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should return original name when no mapping exists")
    void normalize_unmappedName() {
        assertThat(TeamNameNormalizer.normalize("Arsenal")).isEqualTo("Arsenal");
        assertThat(TeamNameNormalizer.normalize("Sunderland")).isEqualTo("Sunderland");
    }

    @Test
    @DisplayName("should handle null and empty input")
    void normalize_nullAndEmpty() {
        assertThat(TeamNameNormalizer.normalize(null)).isNull();
        assertThat(TeamNameNormalizer.normalize("")).isEqualTo("");
        assertThat(TeamNameNormalizer.normalize("  ")).isEqualTo("  ");
    }
}

