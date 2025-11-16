package com.example.ragchatbot.controller;

import com.example.ragchatbot.agent.NcaaBasketballAgent;
import com.example.ragchatbot.service.BigQueryExecutionService;
import com.example.ragchatbot.service.ChatResponseFormatter;
import com.example.ragchatbot.service.SqlQueryStorageService;
import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.response.FormattingResult;
import com.example.ragchatbot.service.response.QueryLogMetadataBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private NcaaBasketballAgent agent;

    @Autowired
    private BigQueryExecutionService bigQueryService;

    @Autowired
    private ChatResponseFormatter formatter;

    @Autowired
    private SqlQueryStorageService sqlQueryStorageService;

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Received chat request: requestId={}, remoteAddr={}, userAgent={}", 
                    requestId, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
            
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                logger.warn("Empty query received: requestId={}", requestId);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("message", "Query cannot be empty");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.info("Processing query: requestId={}, queryLength={}, queryPreview={}", 
                    requestId, query.length(), query.length() > 100 ? query.substring(0, 100) + "..." : query);

            // Check cache by user query text (before generating SQL)
            Map<String, Object> cachedQuery = sqlQueryStorageService.findCachedResultsByQuery(query);
            Map<String, Object> response;
            long queryTime;
            
            if (cachedQuery != null) {
                // Cache hit - use cached results (skip SQL generation)
                logger.info("Using cached results: requestId={}", requestId);
                queryTime = 0;
                
                Map<String, Object> cachedResults = sqlQueryStorageService.getCachedResults(cachedQuery);
                
                // Get cached SQL for logging
                String cachedSql = (String) cachedQuery.get("sql");
                
                // Format response using cached data
                long formatStart = System.currentTimeMillis();
                FormattingResult formattingResult = formatter.formatResponseFromCache(query, cachedSql, cachedResults);
                response = new HashMap<>(formattingResult.getResponseBody());
                long formatTime = System.currentTimeMillis() - formatStart;
                
                // Add cache indicator to response
                response.put("fromCache", true);
                
                long totalTime = System.currentTimeMillis() - startTime;
                logger.info("Request completed from cache: requestId={}, totalTimeMs={}, formatTimeMs={}", 
                        requestId, totalTime, formatTime);
            } else {
                // Cache miss - generate SQL and execute BigQuery
                logger.info("Cache miss, generating SQL and executing BigQuery: requestId={}", requestId);
                
                // Generate SQL using ADK agent
                long sqlGenStart = System.currentTimeMillis();
                String sql = agent.generateSql(query);
                long sqlGenTime = System.currentTimeMillis() - sqlGenStart;
                logger.info("SQL generated: requestId={}, sqlGenerationTimeMs={}, sqlLength={}, sqlPreview={}", 
                        requestId, sqlGenTime, sql.length(), sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
                
                long queryStart = System.currentTimeMillis();
                BigQueryResult bigQueryResult = bigQueryService.executeQueryRich(sql);
                queryTime = System.currentTimeMillis() - queryStart;
                logger.info("Query executed: requestId={}, queryExecutionTimeMs={}, rowCount={}, columnCount={}", 
                        requestId, queryTime, bigQueryResult.getRowCount(), bigQueryResult.getColumnCount());

                // Format response using new transformer pipeline with SQL analysis
                long formatStart = System.currentTimeMillis();
                FormattingResult formattingResult = formatter.formatResponse(query, sql, bigQueryResult);
                response = new HashMap<>(formattingResult.getResponseBody());
                long formatTime = System.currentTimeMillis() - formatStart;

                Map<String, Object> metadata = QueryLogMetadataBuilder.build(sql, formattingResult);
                sqlQueryStorageService.storeQuery(query, sql, bigQueryResult, metadata);

                response.put("fromCache", false);
                
                long totalTime = System.currentTimeMillis() - startTime;
                logger.info("Request completed successfully: requestId={}, totalTimeMs={}, sqlGenTimeMs={}, queryTimeMs={}, formatTimeMs={}", 
                        requestId, totalTime, sqlGenTime, queryTime, formatTime);
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: requestId={}, error={}", requestId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Invalid request: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("Error processing query: requestId={}, error={}, totalTimeMs={}", 
                    requestId, e.getMessage(), totalTime, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error processing query: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            MDC.clear();
        }
    }

    @PostMapping("/queries/process")
    public ResponseEntity<Map<String, Object>> processQueries(HttpServletRequest httpRequest) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        long startTime = System.currentTimeMillis();
        
        try {
            logger.info("Received process queries request: requestId={}, remoteAddr={}", 
                    requestId, httpRequest.getRemoteAddr());
            
            Map<String, Object> stats = sqlQueryStorageService.processQueriesAndUpdateResults();
            
            long totalTime = System.currentTimeMillis() - startTime;
            stats.put("processingTimeMs", totalTime);
            logger.info("Query processing completed: requestId={}, totalTimeMs={}, stats={}", 
                    requestId, totalTime, stats);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("Error processing queries: requestId={}, error={}, totalTimeMs={}", 
                    requestId, e.getMessage(), totalTime, e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error processing queries: " + e.getMessage());
            errorResponse.put("processingTimeMs", totalTime);
            return ResponseEntity.internalServerError().body(errorResponse);
        } finally {
            MDC.clear();
        }
    }
}

