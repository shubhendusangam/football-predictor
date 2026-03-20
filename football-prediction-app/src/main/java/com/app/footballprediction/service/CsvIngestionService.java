package com.app.footballprediction.service;

import org.springframework.stereotype.Service;

import com.app.common.model.Match;
import com.app.common.repository.MatchRepository;
import com.app.common.util.SeasonHelper;
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
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvIngestionService {

   private final MatchRepository matchRepository;

   @Value("${csv.data.paths}")
   private String csvPaths;

   // Use a custom formatter for 2-digit years that interprets:
   // - 00-29 as 2000-2029
   // - 30-99 as 1930-1999
   // This ensures historical football data (e.g., 1993-2025) is parsed correctly
   private static final DateTimeFormatter FMT_SHORT = new java.time.format.DateTimeFormatterBuilder()
         .appendPattern("dd/MM/")
         .appendValueReduced(java.time.temporal.ChronoField.YEAR, 2, 2, 1930)
         .toFormatter();
   private static final DateTimeFormatter FMT_LONG  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

   // ── Public API ────────────────────────────────────────────────────────

   public void ingestAll() {
      // Pre-load all existing match keys once (1 query) instead of per-row existence checks
      Set<String> existingKeys = loadExistingMatchKeys();

      String[] paths = csvPaths.split(",");
      int totalLoaded = 0;

      for (String path : paths) {
         String trimmed = path.trim();
         log.debug("Ingesting CSV: {}", trimmed);
         int count = ingestFileWithKeys(trimmed, existingKeys);
         log.debug("  → {} new matches loaded from {}", count, trimmed);
         totalLoaded += count;
      }

      // Log final DB state after ingestion finishes.
      log.info("CSV ingestion complete: {} files processed, {} new matches loaded, {} total in DB",
            paths.length, totalLoaded, matchRepository.count());
   }

   /**
    * Backfill the {@code season} column for any matches that have it set to {@code NULL}.
    *
    * <p>This handles two cases:
    * <ol>
    *   <li>Matches loaded before the season-extraction logic was added to CSV ingestion</li>
    *   <li>Matches inserted via API sync code that didn't set the season field</li>
    * </ol>
    *
    * <p>The season is derived from {@code matchDate} using {@link SeasonHelper#deriveSeason(LocalDate)}
    * (Aug–Jul football calendar: Oct 2025 → "2025-26", Mar 2026 → "2025-26").</p>
    *
    * @return number of matches updated
    */
   public int backfillMissingSeasons() {
      List<Match> nullSeasonMatches = matchRepository.findAll()
            .stream()
            .filter(m -> m.getSeason() == null && m.getMatchDate() != null)
            .toList();

      if (nullSeasonMatches.isEmpty()) {
         log.debug("Season backfill: all matches already have a season value");
         return 0;
      }

      List<Match> toUpdate = new ArrayList<>(nullSeasonMatches.size());
      for (Match m : nullSeasonMatches) {
         m.setSeason(SeasonHelper.deriveSeason(m.getMatchDate()));
         toUpdate.add(m);
      }

      matchRepository.saveAll(toUpdate);
      log.info("Season backfill: updated {} matches with derived season values", toUpdate.size());
      return toUpdate.size();
   }

   /**
    * Ingests a single CSV file from the classpath, parsing matches and saving new entries to the database.
    * Skips rows that are empty, malformed, or represent future/postponed fixtures without results.
    *
    * @param classpathLocation The location of the CSV file on the classpath (e.g., "data/premier-league-2020.csv").
    * @return The number of new Match records successfully saved to the database.
    */
   public int ingestFile(String classpathLocation) {
      // Public API: loads keys fresh (for standalone calls)
      return ingestFileWithKeys(classpathLocation, loadExistingMatchKeys());
   }

   /**
    * Updates existing matches with missing fouls data (HF/AF columns).
    * This method re-reads CSV files and updates matches that have null fouls values.
    *
    * @return Number of matches updated with fouls data
    */
   public int updateFoulsData() {
      // Pre-load all matches into a map for O(1) lookups (1 query instead of N per-row queries)
      Map<String, Match> matchMap = loadMatchMap();

      String[] paths = csvPaths.split(",");
      int totalUpdated = 0;

      for (String path : paths) {
         String trimmed = path.trim();
         log.debug("Updating fouls data from CSV: {}", trimmed);
         int count = updateFoulsFromFile(trimmed, matchMap);
         log.debug("  → {} matches updated with fouls data from {}", count, trimmed);
         totalUpdated += count;
      }

      log.info("Fouls data update complete: {} files processed, {} matches updated", paths.length, totalUpdated);
      return totalUpdated;
   }

   /**
    * Enriches existing matches with missing statistics from CSV files.
    *
    * <p>When matches are inserted via the external API (football-data.org), they only
    * contain scores (FTHG, FTAG, HTHG, HTAG) but NOT detailed match statistics
    * (shots, shots on target, corners, cards, fouls, etc.). This method re-reads
    * the CSV files and fills in any missing statistics for those matches.</p>
    *
    * <p>This is essential for features like Expected Goals (xG), Corner Stats,
    * Shot Quality, etc. that depend on detailed match statistics.</p>
    *
    * @return Number of matches enriched with statistics
    */
   public int enrichMissingStats() {
      // Pre-load all matches into a map for O(1) lookups (1 query instead of N per-row queries)
      Map<String, Match> matchMap = loadMatchMap();

      String[] paths = csvPaths.split(",");
      int totalEnriched = 0;

      for (String path : paths) {
         String trimmed = path.trim();
         int count = enrichStatsFromFile(trimmed, matchMap);
         if (count > 0) {
            log.debug("  → {} matches enriched with statistics from {}", count, trimmed);
         }
         totalEnriched += count;
      }

      if (totalEnriched > 0) {
         log.info("Stats enrichment complete. Total matches enriched: {}", totalEnriched);
      } else {
         log.debug("Stats enrichment complete. No matches needed enrichment.");
      }
      return totalEnriched;
   }

   /**
    * Enriches existing matches from a single CSV with missing statistics.
    * Checks for missing: HS, AS, HST, AST, HC, AC, HY, AY, HR, AR, HF, AF.
    * Only updates matches that exist in DB but lack these stats.
    * Uses pre-loaded matchMap for O(1) lookups instead of per-row DB queries.
    */
   private int enrichStatsFromFile(String classpathLocation, Map<String, Match> matchMap) {
      List<Match> toUpdate = new ArrayList<>();

      try (CSVReader reader = new CSVReader(new InputStreamReader(new ClassPathResource(classpathLocation).getInputStream()))) {

         String[] headers = reader.readNext();
         if (headers == null) return 0;

         Map<String, Integer> colIndex = buildColumnIndex(headers);

         // Need at least some stats columns to be useful
         boolean hasAnyStats = colIndex.containsKey("HS") || colIndex.containsKey("HST") ||
                               colIndex.containsKey("HC") || colIndex.containsKey("HY") ||
                               colIndex.containsKey("HF");
         if (!hasAnyStats) return 0;

         String[] row;
         int lineNum = 1;

         while ((row = reader.readNext()) != null) {
            lineNum++;
            if (row.length < 8 || row[0].isBlank()) continue;

            try {
               LocalDate date = parseDate(getString(row, colIndex, "Date"));
               if (date == null) continue;

               String homeTeam = getString(row, colIndex, "HomeTeam");
               String awayTeam = getString(row, colIndex, "AwayTeam");
               if (homeTeam == null || awayTeam == null) continue;

               // Use pre-loaded map for O(1) lookup instead of per-row DB query
               String key = date + "|" + homeTeam + "|" + awayTeam;
               Match existing = matchMap.get(key);
               if (existing == null) continue;

               // Check if any stats are missing on the existing match
               boolean updated = false;

               if (existing.getHomeShots() == null && colIndex.containsKey("HS")) {
                  Integer hs = getInt(row, colIndex, "HS");
                  Integer as = getInt(row, colIndex, "AS");
                  if (hs != null) { existing.setHomeShots(hs); existing.setAwayShots(as); updated = true; }
               }
               if (existing.getHomeShotsOnTarget() == null && colIndex.containsKey("HST")) {
                  Integer hst = getInt(row, colIndex, "HST");
                  Integer ast = getInt(row, colIndex, "AST");
                  if (hst != null) { existing.setHomeShotsOnTarget(hst); existing.setAwayShotsOnTarget(ast); updated = true; }
               }
               if (existing.getHomeCorners() == null && colIndex.containsKey("HC")) {
                  Integer hc = getInt(row, colIndex, "HC");
                  Integer ac = getInt(row, colIndex, "AC");
                  if (hc != null) { existing.setHomeCorners(hc); existing.setAwayCorners(ac); updated = true; }
               }
               if (existing.getHomeYellowCards() == null && colIndex.containsKey("HY")) {
                  Integer hy = getInt(row, colIndex, "HY");
                  Integer ay = getInt(row, colIndex, "AY");
                  if (hy != null) { existing.setHomeYellowCards(hy); existing.setAwayYellowCards(ay); updated = true; }
               }
               if (existing.getHomeRedCards() == null && colIndex.containsKey("HR")) {
                  Integer hr = getInt(row, colIndex, "HR");
                  Integer ar = getInt(row, colIndex, "AR");
                  if (hr != null) { existing.setHomeRedCards(hr); existing.setAwayRedCards(ar); updated = true; }
               }
               if (existing.getHomeFouls() == null && colIndex.containsKey("HF")) {
                  Integer hf = getInt(row, colIndex, "HF");
                  Integer af = getInt(row, colIndex, "AF");
                  if (hf != null) { existing.setHomeFouls(hf); existing.setAwayFouls(af); updated = true; }
               }
               if (existing.getHalfTimeHomeGoals() == null && colIndex.containsKey("HTHG")) {
                  Integer hthg = getInt(row, colIndex, "HTHG");
                  Integer htag = getInt(row, colIndex, "HTAG");
                  String htr = getString(row, colIndex, "HTR");
                  if (hthg != null) { existing.setHalfTimeHomeGoals(hthg); existing.setHalfTimeAwayGoals(htag); existing.setHalfTimeResult(htr); updated = true; }
               }
               if (existing.getReferee() == null && colIndex.containsKey("Referee")) {
                  String ref = getString(row, colIndex, "Referee");
                  if (ref != null) { existing.setReferee(ref); updated = true; }
               }
               if (existing.getKickoffTime() == null && colIndex.containsKey("Time")) {
                  String time = getString(row, colIndex, "Time");
                  if (time != null) { existing.setKickoffTime(time); updated = true; }
               }

               if (updated) {
                  toUpdate.add(existing);
               }

            } catch (Exception e) {
               log.warn("Skipping row {} in {} during stats enrichment: {}", lineNum, classpathLocation, e.getMessage());
            }
         }

      } catch (IOException | CsvValidationException e) {
         log.error("Failed to read CSV {} during stats enrichment: {}", classpathLocation, e.getMessage());
         return 0;
      }

      if (!toUpdate.isEmpty()) {
         matchRepository.saveAll(toUpdate);
      }

      return toUpdate.size();
   }

   /**
    * Update fouls data for matches from a single CSV file.
    * Uses pre-loaded matchMap for O(1) lookups instead of per-row DB queries.
    */
   private int updateFoulsFromFile(String classpathLocation, Map<String, Match> matchMap) {
      log.debug("Updating fouls data from: {}", classpathLocation);

      List<Match> toUpdate = new ArrayList<>();

      try (CSVReader reader = new CSVReader(new InputStreamReader(new ClassPathResource(classpathLocation).getInputStream()))) {

         String[] headers = reader.readNext();
         if (headers == null) {
            log.warn("Empty file: {}", classpathLocation);
            return 0;
         }

         Map<String, Integer> colIndex = buildColumnIndex(headers);

         // Check if HF/AF columns exist in this CSV
         if (!colIndex.containsKey("HF") || !colIndex.containsKey("AF")) {
            log.debug("CSV {} does not contain HF/AF columns, skipping", classpathLocation);
            return 0;
         }

         String[] row;
         int lineNum = 1;

         while ((row = reader.readNext()) != null) {
            lineNum++;

            if (row.length < 8 || row[0].isBlank()) {
               continue;
            }

            try {
               LocalDate date = parseDate(getString(row, colIndex, "Date"));
               if (date == null) continue;

               String homeTeam = getString(row, colIndex, "HomeTeam");
               String awayTeam = getString(row, colIndex, "AwayTeam");
               if (homeTeam == null || awayTeam == null) continue;

               Integer homeFouls = getInt(row, colIndex, "HF");
               Integer awayFouls = getInt(row, colIndex, "AF");

               // Skip if no fouls data in CSV
               if (homeFouls == null && awayFouls == null) continue;

               // Use pre-loaded map for O(1) lookup instead of per-row DB query
               String key = date + "|" + homeTeam + "|" + awayTeam;
               Match existing = matchMap.get(key);
               if (existing == null) continue;

               // Update only if fouls data is missing
               if (existing.getHomeFouls() == null || existing.getAwayFouls() == null) {
                  existing.setHomeFouls(homeFouls);
                  existing.setAwayFouls(awayFouls);
                  toUpdate.add(existing);
               }

            } catch (Exception e) {
               log.warn("Skipping row {} in {}: {}", lineNum, classpathLocation, e.getMessage());
            }
         }

      } catch (IOException | CsvValidationException e) {
         log.error("Failed to read CSV {}: {}", classpathLocation, e.getMessage());
         return 0;
      }

      if (!toUpdate.isEmpty()) {
         matchRepository.saveAll(toUpdate);
         log.debug("Updated {} matches with fouls data from {}", toUpdate.size(), classpathLocation);
      }

      return toUpdate.size();
   }


   // ── Private helpers ───────────────────────────────────────────────────

   /**
    * Pre-load all existing match keys for O(1) duplicate detection.
    * Uses a lightweight projection (3 columns) instead of loading full entities.
    */
   private Set<String> loadExistingMatchKeys() {
      List<Object[]> projections = matchRepository.findAllMatchKeyProjections();
      Set<String> keys = new HashSet<>(projections.size());
      for (Object[] r : projections) {
         keys.add(r[0] + "|" + r[1] + "|" + r[2]);
      }
      log.debug("Pre-loaded {} existing match keys for dedup", keys.size());
      return keys;
   }

   /**
    * Pre-load all matches into a map keyed by "date|homeTeam|awayTeam" for O(1) lookups.
    * Used by updateFoulsData() and enrichMissingStats() to avoid per-row DB queries.
    */
   private Map<String, Match> loadMatchMap() {
      List<Match> allMatches = matchRepository.findAll();
      Map<String, Match> map = new HashMap<>(allMatches.size());
      for (Match m : allMatches) {
         String key = m.getMatchDate() + "|" + m.getHomeTeam() + "|" + m.getAwayTeam();
         map.put(key, m);
      }
      log.debug("Pre-loaded {} matches into lookup map", map.size());
      return map;
   }

   /**
    * Internal ingestion method that uses a pre-loaded key set for O(1) duplicate detection.
    * Newly added matches are also added to the key set for cross-file dedup.
    */
   private int ingestFileWithKeys(String classpathLocation, Set<String> existingKeys) {
      long start = System.currentTimeMillis();
      log.debug("Starting ingestion: {}", classpathLocation);

      // Extract season from filename
      String season = extractSeasonFromFilename(classpathLocation);
      if (season != null) {
         log.debug("Detected season: {}", season);
      }

      List<Match> toSave = new ArrayList<>();
      int skippedCount = 0;

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
               skippedCount++;
               continue;
            }

            try {
               Match match = parseRow(row, colIndex, season);
               if (match == null) {
                  skippedCount++;
                  continue;
               }

               // O(1) in-memory duplicate check instead of per-row DB query
               String key = match.getMatchDate() + "|" + match.getHomeTeam() + "|" + match.getAwayTeam();
               if (existingKeys.contains(key)) {
                  skippedCount++;
                  continue;
               }

               toSave.add(match);
               existingKeys.add(key); // Track newly added matches for cross-file dedup

            } catch (Exception e) {
               skippedCount++;
               log.warn("Skipping malformed row {} in {}: {}", lineNum, classpathLocation, e.getMessage());
            }
         }

      } catch (IOException | CsvValidationException e) {
         log.error("Failed to read CSV {}: {}", classpathLocation, e.getMessage());
         return 0;
      }

      long duration = System.currentTimeMillis() - start;
      log.debug("Finished ingestion: {} → {} saved, {} skipped, {}ms",
            classpathLocation, toSave.size(), skippedCount, duration);

      matchRepository.saveAll(toSave);
      return toSave.size();
   }

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

   private Match parseRow(String[] row, Map<String, Integer> col, String season) {
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
            .season(season)  // Set season from filename
            .referee(getString(row, col, "Referee"))
            .kickoffTime(getString(row, col, "Time"))
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
            .homeFouls(getInt(row, col, "HF"))
            .awayFouls(getInt(row, col, "AF"))
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

   /**
    * Extract season string from CSV filename.
    * e.g., "data/PL_23_24.csv" → "2023-24"
    *       "data/PL_99_00.csv" → "1999-00"
    */
   private String extractSeasonFromFilename(String filename) {
      // Extract just the filename without path
      String name = filename.substring(filename.lastIndexOf('/') + 1);
      // Remove extension
      name = name.replace(".csv", "");
      // Expected format: PL_YY_YY (e.g., PL_23_24)
      String[] parts = name.split("_");
      if (parts.length >= 3) {
         String startYear = parts[1];
         String endYear = parts[2];
         // Convert 2-digit year to 4-digit for start year
         int startYearInt = Integer.parseInt(startYear);
         String fullStartYear;
         if (startYearInt >= 93) {
            fullStartYear = "19" + startYear;
         } else {
            fullStartYear = "20" + startYear;
         }
         return fullStartYear + "-" + endYear;
      }
      return null;
   }
}

