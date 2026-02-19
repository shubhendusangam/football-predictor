package com.app.footballprediction.service;

import org.springframework.stereotype.Service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvIngestionService {

   private final MatchRepository matchRepository;

   @Value("${csv.data.paths}")
   private String csvPaths;

   private static final DateTimeFormatter FMT_SHORT = DateTimeFormatter.ofPattern("dd/MM/yy");
   private static final DateTimeFormatter FMT_LONG  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   // ── Public API ────────────────────────────────────────────────────────

   public void ingestAll() {
      // Iterate configured CSV paths and ingest each file, logging counts.
      String[] paths = csvPaths.split(",");
      int totalLoaded = 0;

      for (String path : paths) {
         String trimmed = path.trim();
         log.info("Ingesting CSV: {}", trimmed);
         int count = ingestFile(trimmed);
         log.info("  → {} new matches loaded from {}", count, trimmed);
         totalLoaded += count;
      }

      // Log final DB state after ingestion finishes.
      log.info("CSV ingestion complete. Total matches in DB: {}", matchRepository.count());
   }

   /**
    * Ingests a single CSV file from the classpath, parsing matches and saving new entries to the database.
    * Skips rows that are empty, malformed, or represent future/postponed fixtures without results.
    *
    * @param classpathLocation The location of the CSV file on the classpath (e.g., "data/premier-league-2020.csv").
    * @return The number of new Match records successfully saved to the database.
    */
   public int ingestFile(String classpathLocation) {
      long start = System.currentTimeMillis();
      log.info("Starting ingestion: {}", classpathLocation);

      List<Match> toSave = new ArrayList<>();
      int skippedCount = 0;  // Add this counter

      try (CSVReader reader = new CSVReader(new InputStreamReader(new ClassPathResource(classpathLocation).getInputStream()))) {

         String[] headers = reader.readNext();
         if (headers == null) {
            log.warn("Empty file: {}", classpathLocation);
            return 0;
         }

         Map<String, Integer> colIndex = buildColumnIndex(headers);
         validateRequiredColumns(colIndex, classpathLocation);

         String[] row;
         int lineNum = 1;

         while ((row = reader.readNext()) != null) {
            lineNum++;

            if (row.length < 8 || row[0].isBlank()) {
               skippedCount++;  // Increment when skipping
               continue;
            }

            try {
               Match match = parseRow(row, colIndex);
               if (match == null) {
                  skippedCount++;  // Increment when parseRow returns null
                  continue;
               }

               if (matchRepository.existsByMatchDateAndHomeTeamAndAwayTeam(
                     match.getMatchDate(),
                     match.getHomeTeam(),
                     match.getAwayTeam())) {
                  skippedCount++;  // Increment for duplicates
                  continue;
               }

               toSave.add(match);

            } catch (Exception e) {
               skippedCount++;  // Increment for malformed rows
               log.warn("Skipping malformed row {} in {}: {}", lineNum, classpathLocation, e.getMessage());
            }
         }

      } catch (IOException | CsvValidationException e) {
         log.error("Failed to read CSV {}: {}", classpathLocation, e.getMessage());
         return 0;
      }

      long duration = System.currentTimeMillis() - start;
      log.info("Finished ingestion: {} → {} saved, {} skipped, {}ms",
            classpathLocation, toSave.size(), skippedCount, duration);

      matchRepository.saveAll(toSave);
      return toSave.size();
   }


   // ── Private helpers ───────────────────────────────────────────────────

   private Map<String, Integer> buildColumnIndex(String[] headers) {
      // Build a mapping of header name -> column index for quick lookup while parsing rows.
      Map<String, Integer> map = new HashMap<>();
      for (int i = 0; i < headers.length; i++) {
         map.put(headers[i].trim(), i);
      }
      return map;
   }

   private void validateRequiredColumns(Map<String, Integer> colIndex, String file) {
      // Ensure critical columns exist in the CSV header; throw IllegalArgumentException otherwise.
      List<String> required = List.of("Date", "HomeTeam", "AwayTeam", "FTHG", "FTAG", "FTR");

      for (String col : required) {
         if (!colIndex.containsKey(col)) {
            throw new IllegalArgumentException(
                  "Required column '" + col + "' not found in " + file +
                        ". Available: " + colIndex.keySet());
         }
      }
   }

   private Match parseRow(String[] row, Map<String, Integer> col) {
      // Convert a CSV row into a Match object or return null if the row should be skipped.
      // Skips rows with no final result (future or postponed) or unparsable dates.
      String ftr = getString(row, col, "FTR");

      // Skip future fixtures or postponed matches — no result yet
      if (ftr == null || ftr.isBlank() ||
            (!ftr.equals("H") && !ftr.equals("D") && !ftr.equals("A"))) {
         return null;
      }

      // Parse the match date using multiple formats; return null if date cannot be parsed.
      LocalDate date = parseDate(getString(row, col, "Date"));
      if (date == null) return null;

      // Build and return a Match entity. Optional fields may be null if absent or malformed.
      return Match.builder()
            // Required Phase 1
            .matchDate(date)
            .homeTeam(getString(row, col, "HomeTeam"))
            .awayTeam(getString(row, col, "AwayTeam"))
            .fullTimeHomeGoals(getInt(row, col, "FTHG"))
            .fullTimeAwayGoals(getInt(row, col, "FTAG"))
            .fullTimeResult(ftr)
            // Optional Phase 1
            .halfTimeHomeGoals(getInt(row, col, "HTHG"))
            .halfTimeAwayGoals(getInt(row, col, "HTAG"))
            .halfTimeResult(getString(row, col, "HTR"))
            // Phase 2 stats (null-safe — absent in older CSVs)
            .homeShots(getInt(row, col, "HS"))
            .awayShots(getInt(row, col, "AS"))
            .homeShotsOnTarget(getInt(row, col, "HST"))
            .awayShotsOnTarget(getInt(row, col, "AST"))
            .homeCorners(getInt(row, col, "HC"))
            .awayCorners(getInt(row, col, "AC"))
            .homeYellowCards(getInt(row, col, "HY"))
            .awayYellowCards(getInt(row, col, "AY"))
            .homeRedCards(getInt(row, col, "HR"))
            .awayRedCards(getInt(row, col, "AR"))
            .build();
   }

   // ── Value extractors ──────────────────────────────────────────────────

   private String getString(String[] row, Map<String, Integer> col, String name) {
      // Safely retrieve a trimmed String value from the row by column name.
      // Returns null if column is missing, index out of bounds, or value is empty.
      Integer idx = col.get(name);
      if (idx == null || idx >= row.length) return null;
      String val = row[idx].trim();
      return val.isEmpty() ? null : val;
   }

   private Integer getInt(String[] row, Map<String, Integer> col, String name) {
      // Parse an integer from the named column. Returns null if missing or unparsable.
      String val = getString(row, col, name);
      if (val == null) return null;
      try {
         return Integer.parseInt(val);
      } catch (NumberFormatException e) {
         // Malformed numeric value — treat as absent rather than throwing.
         return null;
      }
   }

   private LocalDate parseDate(String dateStr) {
      // Attempt to parse a date string using short then long formats.
      // Returns parsed LocalDate or null if parsing fails or input is blank.
      if (dateStr == null || dateStr.isBlank()) return null;
      try {
         return LocalDate.parse(dateStr, FMT_SHORT);
      } catch (DateTimeParseException e1) {
         try {
            return LocalDate.parse(dateStr, FMT_LONG);
         } catch (DateTimeParseException e2) {
            // Log and return null when the date cannot be parsed in either format.
            log.warn("Cannot parse date: '{}'", dateStr);
            return null;
         }
      }
   }

   /**
    * Get total match count in database.
    */
   public long getMatchCount() {
      return matchRepository.count();
   }
}