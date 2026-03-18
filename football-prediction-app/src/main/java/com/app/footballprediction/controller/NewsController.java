package com.app.footballprediction.controller;

import com.app.footballprediction.dto.external.NewsResponse;
import com.app.footballprediction.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for football news endpoints.
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "News", description = "Football news from free RSS feeds – Premier League, team-specific, and general")
public class NewsController {

    private final NewsService newsService;

    /**
     * Get Premier League news
     * GET /api/news/premier-league
     */
    @GetMapping("/premier-league")
    public ResponseEntity<?> getPremierLeagueNews() {
        try {
            NewsResponse news = newsService.getPremierLeagueNews();
            return ResponseEntity.ok(news);
        } catch (Exception e) {
            log.error("Failed to fetch PL news: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch news",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get news for a specific team
     * GET /api/news/team?name=Arsenal
     */
    @GetMapping("/team")
    public ResponseEntity<?> getTeamNews(@RequestParam String name) {
        try {
            NewsResponse news = newsService.getTeamNews(name);
            return ResponseEntity.ok(news);
        } catch (Exception e) {
            log.error("Failed to fetch team news: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch team news",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get general football news
     * GET /api/news/football
     */
    @GetMapping("/football")
    public ResponseEntity<?> getFootballNews() {
        try {
            NewsResponse news = newsService.getFootballNews();
            return ResponseEntity.ok(news);
        } catch (Exception e) {
            log.error("Failed to fetch football news: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch news",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * Get aggregated news from multiple categories
     * GET /api/news/all
     */
    @GetMapping("/all")
    public ResponseEntity<?> getAllNews() {
        try {
            Map<String, Object> news = newsService.getAggregatedNews();
            return ResponseEntity.ok(news);
        } catch (Exception e) {
            log.error("Failed to fetch aggregated news: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to fetch news",
                    "details", e.getMessage()
            ));
        }
    }
}
