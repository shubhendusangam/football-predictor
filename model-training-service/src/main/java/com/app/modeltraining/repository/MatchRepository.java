package com.app.modeltraining.repository;

import com.app.modeltraining.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findAllByOrderByMatchDateAsc();

    @Query("SELECT m FROM Match m WHERE m.homeTeam = :team AND m.matchDate < :beforeDate ORDER BY m.matchDate DESC")
    List<Match> findHomeMatchesByTeamBeforeDate(@Param("team") String team, @Param("beforeDate") LocalDate beforeDate);

    @Query("SELECT m FROM Match m WHERE m.awayTeam = :team AND m.matchDate < :beforeDate ORDER BY m.matchDate DESC")
    List<Match> findAwayMatchesByTeamBeforeDate(@Param("team") String team, @Param("beforeDate") LocalDate beforeDate);

    @Query("SELECT m FROM Match m WHERE (m.homeTeam = :team OR m.awayTeam = :team) AND m.matchDate < :beforeDate ORDER BY m.matchDate DESC")
    List<Match> findByTeamBeforeDate(@Param("team") String team, @Param("beforeDate") LocalDate beforeDate);

    @Query("SELECT m FROM Match m WHERE ((m.homeTeam = :team1 AND m.awayTeam = :team2) OR (m.homeTeam = :team2 AND m.awayTeam = :team1)) AND m.matchDate < :beforeDate ORDER BY m.matchDate DESC")
    List<Match> findH2HBeforeDate(@Param("team1") String team1, @Param("team2") String team2, @Param("beforeDate") LocalDate beforeDate);
}

