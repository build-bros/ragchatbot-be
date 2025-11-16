package com.example.ragchatbot.service;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SqlQueryStorageService {

    private static final Logger logger = LoggerFactory.getLogger(SqlQueryStorageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Path storageFile;

    @Value("${sql.storage.file:logs/sql-queries.json}")
    private String storageFilePath;
    
    @Value("${sql.storage.max-result-rows:100}")
    private int maxResultRows;

    @Autowired
    private BigQueryExecutionService bigQueryExecutionService;

    @PostConstruct
    public void initialize() {
        try {
            storageFile = Paths.get(storageFilePath);
            
            // Create parent directories if they don't exist
            Files.createDirectories(storageFile.getParent());
            
            // Create file if it doesn't exist
            if (!Files.exists(storageFile)) {
                Files.createFile(storageFile);
                // Initialize with empty array
                Files.write(storageFile, "[]".getBytes());
                logger.info("Created SQL query storage file: {}", storageFile.toAbsolutePath());
            } else {
                logger.info("Using existing SQL query storage file: {}", storageFile.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to initialize SQL query storage file: {}", storageFilePath, e);
            throw new RuntimeException("Failed to initialize SQL query storage", e);
        }
    }

    /**
     * Stores query and SQL without results (backward compatible).
     */
    public void storeQuery(String userQuery, String generatedSql) {
        storeQuery(userQuery, generatedSql, null, null);
    }
    
    /**
     * Stores query, SQL, and BigQuery results.
     * 
     * @param userQuery The user's natural language query
     * @param generatedSql The generated SQL query
     * @param bigQueryResult The BigQuery result (optional, can be null)
     */
    public void storeQuery(String userQuery, String generatedSql, BigQueryResult bigQueryResult) {
        storeQuery(userQuery, generatedSql, bigQueryResult, null);
    }

    /**
     * Stores query, SQL, BigQuery results, and analysis metadata.
     *
     * @param userQuery The user's natural language query
     * @param generatedSql The generated SQL query
     * @param bigQueryResult The BigQuery result (optional, can be null)
     * @param analysisMetadata Additional metadata used for downstream analysis
     */
    public void storeQuery(String userQuery, String generatedSql, BigQueryResult bigQueryResult,
                           Map<String, Object> analysisMetadata) {
        try {
            synchronized (this) {
                // Read existing queries
                List<Map<String, Object>> queries = readQueries();
                
                // Check if this query already exists (don't store duplicates)
                String normalizedNewQuery = normalizeQueryText(userQuery);
                for (Map<String, Object> existingQuery : queries) {
                    String existingQueryText = (String) existingQuery.get("query");
                    if (existingQueryText != null && 
                        normalizeQueryText(existingQueryText).equals(normalizedNewQuery)) {
                        logger.debug("Query already exists, skipping storage: query={}", 
                                userQuery.substring(0, Math.min(50, userQuery.length())));
                        return;
                    }
                }
                
                // Create new query entry
                Map<String, Object> queryEntry = new HashMap<>();
                queryEntry.put("query", userQuery);
                queryEntry.put("sql", generatedSql);
                queryEntry.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                // Add results if available
                if (bigQueryResult != null) {
                    Map<String, Object> results = new HashMap<>();
                    results.put("columns", bigQueryResult.getColumnNames());
                    results.put("columnTypes", bigQueryResult.getColumnTypes().stream()
                            .map(Enum::name)
                            .collect(Collectors.toList()));
                    results.put("rowCount", bigQueryResult.getRowCount());
                    results.put("columnCount", bigQueryResult.getColumnCount());
                    
                    // Store rows (limit to maxResultRows to prevent huge files)
                    List<List<Object>> allRows = bigQueryResult.getAllRows();
                    int rowsToStore = Math.min(allRows.size(), maxResultRows);
                    List<List<Object>> rowsToSave = new ArrayList<>();
                    for (int i = 0; i < rowsToStore; i++) {
                        rowsToSave.add(allRows.get(i));
                    }
                    results.put("rows", rowsToSave);
                    
                    if (allRows.size() > maxResultRows) {
                        results.put("truncated", true);
                        results.put("totalRows", allRows.size());
                        logger.debug("Result truncated: stored {} of {} rows", rowsToStore, allRows.size());
                    } else {
                        results.put("truncated", false);
                    }
                    
                    queryEntry.put("results", results);
                }

                if (analysisMetadata != null && !analysisMetadata.isEmpty()) {
                    queryEntry.put("analysis", analysisMetadata);
                    Object normalized = analysisMetadata.get("normalizedSql");
                    if (normalized instanceof String normalizedSql && !normalizedSql.isEmpty()) {
                        queryEntry.put("normalizedSql", normalizedSql);
                    }
                }
                
                // Add to list
                queries.add(queryEntry);
                
                // Write back to file
                writeQueries(queries);
                
                logger.debug("Stored SQL query: queryLength={}, sqlLength={}, hasResults={}", 
                        userQuery.length(), generatedSql.length(), bigQueryResult != null);
            }
        } catch (Exception e) {
            logger.error("Failed to store SQL query: error={}", e.getMessage(), e);
            // Don't throw exception - storage failure shouldn't break the main flow
        }
    }

    private List<Map<String, Object>> readQueries() throws IOException {
        if (!Files.exists(storageFile) || Files.size(storageFile) == 0) {
            return new ArrayList<>();
        }
        
        String content = Files.readString(storageFile);
        if (content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        CollectionType listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, Map.class);
        return objectMapper.readValue(content, listType);
    }

    private void writeQueries(List<Map<String, Object>> queries) throws IOException {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(queries);
        Files.writeString(storageFile, json);
    }

    /**
     * Finds cached query results by user query text.
     * 
     * @param userQuery The user's natural language query
     * @return Map containing the cached query data, or null if not found or no results
     */
    public Map<String, Object> findCachedResultsByQuery(String userQuery) {
        try {
            synchronized (this) {
                List<Map<String, Object>> queries = readQueries();
                String normalizedInput = normalizeQueryText(userQuery);
                
                for (Map<String, Object> queryEntry : queries) {
                    String cachedQuery = (String) queryEntry.get("query");
                    
                    // Match on normalized query text
                    if (cachedQuery != null && 
                        normalizeQueryText(cachedQuery).equals(normalizedInput) &&
                        queryEntry.containsKey("results") && 
                        queryEntry.get("results") != null) {
                        
                        logger.info("Cache hit: queryLength={}, normalizedQuery={}", 
                                userQuery.length(), normalizedInput.substring(0, Math.min(50, normalizedInput.length())));
                        return queryEntry;
                    }
                }
                
                logger.debug("Cache miss: queryLength={}", userQuery.length());
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to search cache by query: error={}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Normalizes user query text for comparison.
     * Removes punctuation, extra whitespace, and converts to lowercase.
     */
    private String normalizeQueryText(String query) {
        if (query == null) {
            return "";
        }
        
        return query
            .toLowerCase()
            .replaceAll("[^a-z0-9\\s]", "")       // Remove punctuation
            .replaceAll("\\s+", " ")              // Normalize whitespace
            .trim();
    }

    /**
     * Converts cached results to BigQueryResult format.
     * Note: This creates a mock result since we can't recreate TableResult.
     * We'll need to pass data directly to the formatter instead.
     * 
     * @param cachedQuery The cached query entry
     * @return Map containing the cached results, or null if no results
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedResults(Map<String, Object> cachedQuery) {
        Map<String, Object> results = (Map<String, Object>) cachedQuery.get("results");
        if (results == null) {
            return null;
        }
        
        // Return the results map which contains:
        // - columns: List<String>
        // - columnTypes: List<String>
        // - rows: List<List<Object>>
        // - rowCount, columnCount, truncated, etc.
        return results;
    }

    /**
     * Processes the SQL queries JSON file by:
     * 1. Removing duplicates (based on SQL query)
     * 2. Executing queries without results via BigQuery
     * 3. Updating the JSON file with results
     * 
     * @return Map containing statistics about the processing
     */
    public Map<String, Object> processQueriesAndUpdateResults() {
        Map<String, Object> stats = new HashMap<>();
        int duplicatesRemoved = 0;
        int queriesProcessed = 0;
        int queriesSucceeded = 0;
        int queriesFailed = 0;
        List<String> errors = new ArrayList<>();

        try {
            synchronized (this) {
                logger.info("Starting query processing: reading queries from {}", storageFile.toAbsolutePath());
                
                // Read all queries
                List<Map<String, Object>> queries = readQueries();
                int originalCount = queries.size();
                logger.info("Read {} queries from file", originalCount);

                // Remove duplicates based on SQL query (keep the first occurrence)
                Map<String, Map<String, Object>> uniqueQueries = new LinkedHashMap<>();
                for (Map<String, Object> query : queries) {
                    String sql = (String) query.get("sql");
                    if (sql != null && !uniqueQueries.containsKey(sql)) {
                        uniqueQueries.put(sql, query);
                    } else if (sql != null) {
                        duplicatesRemoved++;
                        logger.debug("Removed duplicate query with SQL: {}", 
                                sql.length() > 100 ? sql.substring(0, 100) + "..." : sql);
                    }
                }

                List<Map<String, Object>> deduplicatedQueries = new ArrayList<>(uniqueQueries.values());
                logger.info("Removed {} duplicates, {} unique queries remaining", 
                        duplicatesRemoved, deduplicatedQueries.size());

                // Process queries without results
                for (Map<String, Object> queryEntry : deduplicatedQueries) {
                    // Check if query already has results
                    if (queryEntry.containsKey("results") && queryEntry.get("results") != null) {
                        continue;
                    }

                    String sql = (String) queryEntry.get("sql");
                    if (sql == null || sql.trim().isEmpty()) {
                        logger.warn("Skipping query entry with empty SQL");
                        continue;
                    }

                    queriesProcessed++;
                    logger.info("Processing query {}/{}: sqlLength={}, sqlPreview={}", 
                            queriesProcessed, deduplicatedQueries.size(), 
                            sql.length(), sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);

                    try {
                        // Execute query via BigQuery
                        BigQueryResult bigQueryResult = bigQueryExecutionService.executeQueryRich(sql);
                        
                        // Update query entry with results
                        Map<String, Object> results = new HashMap<>();
                        results.put("columns", bigQueryResult.getColumnNames());
                        results.put("columnTypes", bigQueryResult.getColumnTypes().stream()
                                .map(Enum::name)
                                .collect(Collectors.toList()));
                        results.put("rowCount", bigQueryResult.getRowCount());
                        results.put("columnCount", bigQueryResult.getColumnCount());
                        
                        // Store rows (limit to maxResultRows)
                        List<List<Object>> allRows = bigQueryResult.getAllRows();
                        int rowsToStore = Math.min(allRows.size(), maxResultRows);
                        List<List<Object>> rowsToSave = new ArrayList<>();
                        for (int i = 0; i < rowsToStore; i++) {
                            rowsToSave.add(allRows.get(i));
                        }
                        results.put("rows", rowsToSave);
                        
                        if (allRows.size() > maxResultRows) {
                            results.put("truncated", true);
                            results.put("totalRows", allRows.size());
                        } else {
                            results.put("truncated", false);
                        }
                        
                        queryEntry.put("results", results);
                        queriesSucceeded++;
                        
                        logger.info("Successfully processed query {}/{}: rowCount={}, columnCount={}", 
                                queriesProcessed, deduplicatedQueries.size(), 
                                bigQueryResult.getRowCount(), bigQueryResult.getColumnCount());
                    } catch (Exception e) {
                        queriesFailed++;
                        String errorMsg = String.format("Failed to execute query: %s", e.getMessage());
                        errors.add(errorMsg);
                        logger.error("Failed to process query {}/{}: error={}", 
                                queriesProcessed, deduplicatedQueries.size(), e.getMessage(), e);
                    }
                }

                // Write updated queries back to file
                writeQueries(deduplicatedQueries);
                logger.info("Updated queries written to file: totalQueries={}, processed={}, succeeded={}, failed={}", 
                        deduplicatedQueries.size(), queriesProcessed, queriesSucceeded, queriesFailed);

                // Build statistics
                stats.put("originalCount", originalCount);
                stats.put("duplicatesRemoved", duplicatesRemoved);
                stats.put("finalCount", deduplicatedQueries.size());
                stats.put("queriesProcessed", queriesProcessed);
                stats.put("queriesSucceeded", queriesSucceeded);
                stats.put("queriesFailed", queriesFailed);
                if (!errors.isEmpty()) {
                    stats.put("errors", errors);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to process queries: error={}", e.getMessage(), e);
            stats.put("error", e.getMessage());
            throw new RuntimeException("Failed to process queries", e);
        }

        return stats;
    }

    /**
     * Returns a snapshot of all stored queries for analytical purposes.
     */
    public List<Map<String, Object>> readAllQueriesSnapshot() {
        try {
            synchronized (this) {
                return new ArrayList<>(readQueries());
            }
        } catch (IOException e) {
            logger.error("Failed to read SQL query snapshot: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
}

