package com.app.footballprediction.controller;

import com.app.common.repository.MatchRepository;
import com.app.footballprediction.scheduler.DataUpdateScheduler;
import com.app.footballprediction.service.CsvIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for data lifecycle operations (CSV reload, DB reset, update).
 *
 * Endpoints:
 * - POST /api/data/reload  — re-ingest CSVs
 * - POST /api/data/reset   — delete all + re-ingest
 * - POST /api/data/update  — fetch latest + retrain model
 */
@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
@Slf4j
public class DataManagementController {

    private final CsvIngestionService csvIngestionService;
    private final DataUpdateScheduler dataUpdateScheduler;
    private final MatchRepository matchRepository;

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadData() {
        log.info("CSV reload requested via API...");
        csvIngestionService.ingestAll();
        return ResponseEntity.ok(Map.of("status", "CSV data reloaded successfully"));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetData() {
        log.info("Data reset requested via API - clearing all matches...");
        long deletedCount = matchRepository.count();
        matchRepository.deleteAll();
        log.info("Deleted {} matches, now re-ingesting...", deletedCount);

        csvIngestionService.ingestAll();
        long newCount = matchRepository.count();

        return ResponseEntity.ok(Map.of(
                "status", "Data reset completed successfully",
                "deleted", deletedCount,
                "ingested", newCount
        ));
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateData() {
        log.info("Data update requested via API...");
        String result = dataUpdateScheduler.triggerManualUpdate();
        return ResponseEntity.ok(Map.of("status", "success", "result", result));
    }
}

