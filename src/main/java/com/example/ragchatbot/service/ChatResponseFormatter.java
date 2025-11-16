package com.example.ragchatbot.service;

import com.example.ragchatbot.service.analysis.ResultStatsCollector;
import com.example.ragchatbot.service.analysis.ResultStatsSummary;
import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.data.transformer.ResultTransformer;
import com.example.ragchatbot.service.data.transformer.TransformerFactory;
import com.example.ragchatbot.service.response.FormattingResult;
import com.example.ragchatbot.service.response.QueryLogInsightsService;
import com.example.ragchatbot.service.response.QueryLogRecommendation;
import com.example.ragchatbot.service.response.ResponseTemplateEngine;
import com.example.ragchatbot.service.response.TemplateResult;
import com.example.ragchatbot.service.visualization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ChatResponseFormatter {

    private static final Logger logger = LoggerFactory.getLogger(ChatResponseFormatter.class);

    private final QueryAnalyzer queryAnalyzer;
    private final TransformerFactory transformerFactory;
    private final List<VisualizationStrategy> strategies;
    private final ResultStatsCollector resultStatsCollector;
    private final ResponseTemplateEngine templateEngine;
    private final QueryLogInsightsService queryLogInsightsService;

    @Autowired
    public ChatResponseFormatter(QueryAnalyzer queryAnalyzer,
                                 TransformerFactory transformerFactory,
                                 List<VisualizationStrategy> strategies,
                                 ResultStatsCollector resultStatsCollector,
                                 ResponseTemplateEngine templateEngine,
                                 QueryLogInsightsService queryLogInsightsService) {
        this.queryAnalyzer = queryAnalyzer;
        this.transformerFactory = transformerFactory;
        this.strategies = strategies != null ? new ArrayList<>(strategies) : new ArrayList<>();
        this.resultStatsCollector = resultStatsCollector;
        this.templateEngine = templateEngine;
        this.queryLogInsightsService = queryLogInsightsService;

        this.strategies.sort((s1, s2) -> Integer.compare(s2.getPriority(), s1.getPriority()));
        logger.info("ChatResponseFormatter initialized with {} strategies and transformer factory", this.strategies.size());
    }

    /**
     * Formats response using the new transformer pipeline with BigQueryResult.
     * This is the preferred method that uses optimized data transformation.
     */
    public FormattingResult formatResponse(String userQuery, BigQueryResult bigQueryResult) {
        return formatResponse(userQuery, null, bigQueryResult);
    }
    
    /**
     * Formats response using the new transformer pipeline with BigQueryResult and SQL.
     * This is the preferred method that uses optimized data transformation with SQL analysis.
     */
    public FormattingResult formatResponse(String userQuery, String sql, BigQueryResult bigQueryResult) {
        long startTime = System.currentTimeMillis();
        if (bigQueryResult == null || bigQueryResult.getRowCount() == 0) {
            logger.info("No data found for query: userQueryLength={}", userQuery != null ? userQuery.length() : 0);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No data found for your query.");
            TemplateResult template = templateEngine.buildTemplate(userQuery, "table",
                    queryAnalyzer.analyze(userQuery, sql, bigQueryResult), ResultStatsSummary.empty());
            return new FormattingResult(response,
                    queryAnalyzer.analyze(userQuery, sql, bigQueryResult),
                    ResultStatsSummary.empty(),
                    "table",
                    template,
                    Optional.empty());
        }

        logger.debug("Formatting response (rich): columnCount={}, rowCount={}",
                bigQueryResult.getColumnCount(), bigQueryResult.getRowCount());

        ResultStatsSummary statsSummary = resultStatsCollector.summarize(bigQueryResult);
        QueryIntent queryIntent = queryAnalyzer.analyze(userQuery, sql, bigQueryResult);
        Optional<QueryLogRecommendation> recommendation = queryLogInsightsService.findRecommendation(sql, userQuery);

        String targetChartType = determineTargetChartType(queryIntent, recommendation);

        ResultTransformer transformer = transformerFactory.getTransformer(bigQueryResult, queryIntent, targetChartType);
        TransformedData transformedData = transformer.transform(bigQueryResult);
        VisualizationStrategy strategy = selectStrategy(transformedData, targetChartType);

        if (strategy == null) {
            logger.warn("No strategy found for transformed data, using table fallback");
            strategy = strategies.stream()
                    .filter(s -> s.getChartType().equals("table"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Table strategy not found"));
        }

        Map<String, Object> formattedData = strategy.format(transformedData);
        String chartType = strategy.getChartType();

        TemplateResult template = templateEngine.buildTemplate(userQuery, chartType, queryIntent, statsSummary);
        Map<String, Object> response = new HashMap<>();
        if ("table".equals(chartType)) {
            response.put("tableData", formattedData);
        } else {
            response.put("graphData", formattedData);
        }
        response.put("message", template.composeMessage());

        long formatTime = System.currentTimeMillis() - startTime;
        logger.info("Response formatted (rich): formatTimeMs={}, chartType={}, rowCount={}",
                formatTime, chartType, bigQueryResult.getRowCount());

        return new FormattingResult(response, queryIntent, statsSummary, chartType, template, recommendation);
    }

    /**
     * Formats response using cached data from sql-queries.json.
     * Reconstructs BigQueryResult from cached data and uses the same formatting pipeline
     * as non-cached responses to ensure consistent output.
     * 
     * @param userQuery The user's natural language query
     * @param sql The generated SQL
     * @param cachedResults The cached results map (columns, columnTypes, rows, etc.)
     * @return Formatted response map
     */
    public FormattingResult formatResponseFromCache(String userQuery, String sql, Map<String, Object> cachedResults) {
        logger.info("Formatting response from cache: rowCount={}", cachedResults.get("rowCount"));
        
        // Extract data from cache
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) cachedResults.get("columns");
        @SuppressWarnings("unchecked")
        List<String> columnTypes = (List<String>) cachedResults.get("columnTypes");
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) cachedResults.get("rows");
        
        if (rows == null || rows.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No data found for your query.");
            TemplateResult template = templateEngine.buildTemplate(userQuery, "table",
                    queryAnalyzer.analyze(userQuery, sql, null), ResultStatsSummary.empty());
            return new FormattingResult(response, queryAnalyzer.analyze(userQuery, sql, null),
                    ResultStatsSummary.empty(), "table", template, Optional.empty());
        }

        BigQueryResult bigQueryResult = BigQueryResult.fromCachedData(columns, columnTypes, rows);
        return formatResponse(userQuery, sql, bigQueryResult);
    }

    /**
     * Legacy method for backward compatibility.
     * Converts List<List<Object>> to BigQueryResult format and uses the new pipeline.
     */
    public FormattingResult formatResponse(String userQuery, List<String> columnNames, List<List<Object>> rows) {
        // For backward compatibility, we'll create a simple wrapper
        // Note: This loses some metadata but maintains compatibility
        logger.debug("Using legacy formatResponse method, converting to rich format");
        
        if (rows == null || rows.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "No data found for your query.");
            TemplateResult template = templateEngine.buildTemplate(userQuery, "table",
                    queryAnalyzer.analyze(userQuery), ResultStatsSummary.empty());
            return new FormattingResult(response, queryAnalyzer.analyze(userQuery),
                    ResultStatsSummary.empty(), "table", template, Optional.empty());
        }

        Map<String, Object> legacyResponse = formatResponseLegacy(userQuery, columnNames, rows);
        TemplateResult template = templateEngine.buildTemplate(userQuery, "table",
                queryAnalyzer.analyze(userQuery), ResultStatsSummary.empty());
        return new FormattingResult(legacyResponse, queryAnalyzer.analyze(userQuery),
                ResultStatsSummary.empty(), "table", template, Optional.empty());
    }
    
    /**
     * Legacy implementation for backward compatibility.
     */
    private Map<String, Object> formatResponseLegacy(String userQuery, List<String> columnNames, List<List<Object>> rows) {
        long startTime = System.currentTimeMillis();
        logger.debug("Formatting response (legacy): columnCount={}, rowCount={}", 
                columnNames != null ? columnNames.size() : 0, rows != null ? rows.size() : 0);
        Map<String, Object> response = new HashMap<>();
        
        if (rows == null || rows.isEmpty()) {
            logger.info("No data found for query: userQueryLength={}", userQuery.length());
            response.put("message", "No data found for your query.");
            return response;
        }

        // Analyze query intent (legacy path - no SQL or result structure available)
        QueryIntent queryIntent = queryAnalyzer.analyze(userQuery);
        
        // Create visualization context for legacy path
        VisualizationContext context = new VisualizationContext(
            userQuery, 
            columnNames != null ? columnNames : new ArrayList<>(), 
            rows, 
            queryIntent
        );
        
        // For legacy path, create a simple transformed data from context
        // This is a simplified transformation
        String targetChartType = determineTargetChartType(queryIntent, Optional.empty());
        TransformedData transformedData = createLegacyTransformedData(context, queryIntent, targetChartType);
        
        // Find matching strategy
        VisualizationStrategy selectedStrategy = selectStrategy(transformedData, targetChartType);
        
        if (selectedStrategy == null) {
            logger.warn("No strategy found, using table fallback");
            selectedStrategy = strategies.stream()
                .filter(s -> s.getChartType().equals("table"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Table strategy not found"));
        }
        
        // Format using selected strategy
        Map<String, Object> formattedData = selectedStrategy.format(transformedData);
        String chartType = selectedStrategy.getChartType();
        
        // Build response
        if ("table".equals(chartType)) {
            response.put("message", "Here are the results:");
            response.put("tableData", formattedData);
        } else {
            response.put("message", "Here's the visualization of your data:");
            response.put("graphData", formattedData);
        }

        long formatTime = System.currentTimeMillis() - startTime;
        logger.info("Response formatted (legacy): formatTimeMs={}, chartType={}, rowCount={}", 
                formatTime, chartType, rows.size());

        return response;
    }
    
    /**
     * Creates a simple TransformedData from legacy VisualizationContext.
     * This is a bridge method for backward compatibility.
     */
    private TransformedData createLegacyTransformedData(VisualizationContext context, QueryIntent intent, String requestedChartType) {
        // Use transformer factory logic to determine chart type
        // For now, create a basic transformed data structure
        String effectiveChartType = requestedChartType != null
                ? requestedChartType
                : intent != null ? intent.getPreferredChartType() : "table";
        TransformedData transformedData = new TransformedData(effectiveChartType);
        
        // Add basic data structure - this is simplified
        // In practice, should use proper transformers
        if ("table".equals(effectiveChartType)) {
            transformedData.put("columns", context.getColumnNames());
            transformedData.put("rows", context.getRows());
        } else {
            // For charts, would need proper transformation
            // This is a fallback
            transformedData.put("columns", context.getColumnNames());
            transformedData.put("rows", context.getRows());
        }
        
        return transformedData;
    }
    
    /**
     * Selects the best matching strategy based on transformed data.
     */
    private VisualizationStrategy selectStrategy(TransformedData transformedData, String requestedChartType) {
        if (requestedChartType != null) {
            for (VisualizationStrategy strategy : strategies) {
                if (strategy.getChartType().equalsIgnoreCase(requestedChartType)
                        && strategy.canHandle(transformedData)) {
                    logger.debug("Selected preferred strategy: chartType={}, priority={}",
                            strategy.getChartType(), strategy.getPriority());
                    return strategy;
                }
            }
        }

        for (VisualizationStrategy strategy : strategies) {
            if (strategy.canHandle(transformedData)) {
                logger.debug("Selected strategy: chartType={}, priority={}",
                        strategy.getChartType(), strategy.getPriority());
                return strategy;
            }
        }

        return strategies.stream()
                .filter(s -> s.getChartType().equals("table"))
                .findFirst()
                .orElse(null);
    }

    private String determineTargetChartType(QueryIntent queryIntent, Optional<QueryLogRecommendation> recommendation) {
        if (queryIntent != null && queryIntent.hasExplicitRequest()) {
            String explicitType = queryIntent.getExplicitChartType();
            if (explicitType != null && !explicitType.isBlank()) {
                logger.debug("Honoring explicit chart request: {}", explicitType);
                return explicitType.trim().toLowerCase(Locale.ENGLISH);
            }
        }

        if (recommendation != null && recommendation.isPresent()) {
            String recommendedType = recommendation.map(QueryLogRecommendation::getChartType).orElse(null);
            if (recommendedType != null && !recommendedType.isBlank()) {
                logger.debug("Applying recommendation chart type: {}", recommendedType);
                return recommendedType.trim().toLowerCase(Locale.ENGLISH);
            }
        }

        if (queryIntent != null) {
            return queryIntent.getPreferredChartType();
        }

        return "table";
    }
    
}

