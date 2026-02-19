# Football Prediction Common

Shared library module containing common entities, repositories, and services used by both the main application and the model training service.

## Overview

This module provides:
- **Common Entities**: Shared JPA entities (Match model)
- **Shared Repositories**: Database access layer
- **Feature Engineering**: Common feature calculation services
- **Utility Classes**: Shared utilities and helpers

## Module Structure

```
football-prediction-common/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/app/common/
│   │   │       ├── model/           # JPA entities
│   │   │       ├── repository/      # Spring Data repositories
│   │   │       └── service/         # Shared services
│   │   └── resources/
│   └── test/
├── pom.xml
└── README.md
```

## Key Components

### Entities

#### Match
The core entity representing a football match with:
- Match identification (date, home/away teams)
- Full-time results (FTHG, FTAG, FTR)
- Half-time results (HTHG, HTAG, HTR)
- Match statistics (shots, corners, cards)
- Utility methods for team-based calculations

```java
public class Match {
    private LocalDate matchDate;
    private String homeTeam;
    private String awayTeam;
    private Integer fullTimeHomeGoals;
    private Integer fullTimeAwayGoals;
    private String fullTimeResult;  // H, D, A
    // ... additional fields
    
    // Utility methods
    public int getPointsForTeam(String teamName);
    public int getGoalsScoredByTeam(String teamName);
    public int getGoalsConcededByTeam(String teamName);
}
```

### Repositories

#### MatchRepository
Spring Data JPA repository with custom queries:
- `findByTeamBeforeDate()` - All matches for a team before a date
- `findHomeMatchesByTeamBeforeDate()` - Home matches only
- `findAwayMatchesByTeamBeforeDate()` - Away matches only
- Standard CRUD operations

### Services

#### FeatureEngineeringService
Calculates the 25 statistical features used for predictions:

**Phase 1 - Core Statistics:**
- Form points (last 5 matches)
- Goals scored/conceded averages
- H2H win rates
- Total goals average

**Phase 2 - Match Statistics:**
- Shots on target averages
- Corners averages

**Phase 3 - Advanced Metrics:**
- Goal difference
- Overall form rating
- Win streaks
- Unbeaten streaks
- Rest days between matches

## Usage

This module is included as a dependency in:
- `football-prediction-app`
- `model-training-service`

### Maven Dependency

```xml
<dependency>
    <groupId>com.app</groupId>
    <artifactId>football-prediction-common</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Building

```bash
# Build the module
cd football-prediction-common
mvn clean install

# Or build from parent
cd ..
mvn clean install
```

## Dependencies

- Spring Data JPA
- Jakarta Persistence
- Lombok
- SLF4J for logging

## License

Same as parent project (MIT License).

