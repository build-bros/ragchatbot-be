package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.analysis.ResultStatsCollector;
import com.example.ragchatbot.service.analysis.ResultStatsSummary;
import com.example.ragchatbot.service.data.BigQueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes user queries, SQL patterns, and result structures to extract visualization intent
 * and suggest appropriate chart types.
 */
@Component
public class QueryAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryAnalyzer.class);
    
    // Explicit chart type detection pattern
    private static final Pattern EXPLICIT_CHART_PATTERN = Pattern.compile(
        "(show|display|create|generate|visualize|make|give me|plot|as|in)\\s+.*?\\b(line|bar|pie|bubble|table|chart|graph)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Keywords for different visualization types (fallback)
    private static final String[] TREND_KEYWORDS = {
        "trend", "trends", "over time", "timeline", "history", "historical",
        "change", "changes", "evolution", "progression", "growth", "decline",
        "increase", "decrease", "over years", "over seasons", "by year", "by season"
    };
    
    private static final String[] COMPARISON_KEYWORDS = {
        "compare", "comparison", "versus", "vs", "vs.", "against",
        "top", "bottom", "highest", "lowest", "best", "worst",
        "ranking", "rankings", "ranked", "leader", "leaders",
        "most", "least", "greater", "lesser", "more than", "less than"
    };
    
    private static final String[] DISTRIBUTION_KEYWORDS = {
        "distribution", "distribute", "share", "shares", "percentage", "percent",
        "proportion", "proportions", "breakdown", "break down", "composition",
        "split", "divided", "ratio", "ratios", "part", "parts", "portion"
    };
    
    private static final String[] CORRELATION_KEYWORDS = {
        "relationship", "relationships", "correlation", "correlations",
        "correlate", "related", "connection", "connections", "association",
        "compare", "comparison" // Also used for multi-metric comparisons
    };
    
    // Scoring weights
    private static final double EXPLICIT_REQUEST_WEIGHT = 100.0;
    private static final double SQL_PATTERN_WEIGHT = 10.0;
    private static final double RESULT_STRUCTURE_WEIGHT = 5.0;
    private static final double KEYWORD_WEIGHT = 1.0;
    
    private final SqlPatternAnalyzer sqlPatternAnalyzer;
    private final QueryPatternDetector patternDetector;
    private final ResultStatsCollector resultStatsCollector;
    
    @Autowired
    public QueryAnalyzer(SqlPatternAnalyzer sqlPatternAnalyzer,
                         QueryPatternDetector patternDetector,
                         ResultStatsCollector resultStatsCollector) {
        this.sqlPatternAnalyzer = sqlPatternAnalyzer;
        this.patternDetector = patternDetector;
        this.resultStatsCollector = resultStatsCollector;
    }
    
    /**
     * Analyzes a user query and returns the detected visualization intent.
     * This is the legacy method for backward compatibility.
     */
    public QueryIntent analyze(String userQuery) {
        return analyze(userQuery, null, null);
    }
    
    /**
     * Analyzes user query, SQL, and result structure to determine visualization intent.
     * 
     * @param userQuery The user's natural language query
     * @param sql The generated SQL query (optional)
     * @param bigQueryResult The BigQuery result structure (optional)
     * @return QueryIntent with detected visualization preferences
     */
    public QueryIntent analyze(String userQuery, String sql, BigQueryResult bigQueryResult) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return createDefaultIntent();
        }
        
        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 0.0);
        scores.put("bar", 0.0);
        scores.put("pie", 0.0);
        scores.put("bubble", 0.0);
        scores.put("table", 0.1); // Default fallback score
        
        String explicitChartType = null;
        boolean hasExplicitRequest = false;
        SqlAnalysisResult sqlAnalysis = null;
        ResultStatsSummary statsSummary = null;
        
        // 1. Detect explicit chart type requests (highest priority)
        Optional<String> explicitType = detectExplicitChartType(userQuery);
        if (explicitType.isPresent()) {
            explicitChartType = explicitType.get();
            hasExplicitRequest = true;
            scores.put(explicitChartType, EXPLICIT_REQUEST_WEIGHT);
            logger.debug("Explicit chart type detected: {}", explicitChartType);
        }
        
        // 2. Analyze SQL patterns (high priority)
        if (sql != null && !sql.trim().isEmpty()) {
            sqlAnalysis = sqlPatternAnalyzer.analyze(sql);
            Map<String, Double> sqlScores = sqlAnalysis.getChartTypeScores();
            for (Map.Entry<String, Double> entry : sqlScores.entrySet()) {
                scores.put(entry.getKey(), scores.getOrDefault(entry.getKey(), 0.0) + 
                          entry.getValue() * SQL_PATTERN_WEIGHT);
            }
            logger.debug("SQL analysis scores: {}", sqlScores);
        }
        
        // 3. Analyze result structure (medium priority)
        if (bigQueryResult != null) {
            statsSummary = resultStatsCollector.summarize(bigQueryResult);
            Map<String, Double> resultScores = analyzeResultStructure(bigQueryResult, statsSummary);
            for (Map.Entry<String, Double> entry : resultScores.entrySet()) {
                scores.put(entry.getKey(), scores.getOrDefault(entry.getKey(), 0.0) + 
                          entry.getValue() * RESULT_STRUCTURE_WEIGHT);
            }
            logger.debug("Result structure scores: {}", resultScores);
        }
        
        // 4. Pattern-based detection (medium priority, only if no explicit request)
        if (!hasExplicitRequest) {
            // Use pattern detector for more accurate detection
            if (patternDetector.isRankingQuery(userQuery)) {
                scores.put("bar", scores.getOrDefault("bar", 0.0) + 3.0 * KEYWORD_WEIGHT);
                logger.debug("Ranking pattern detected, boosting bar chart score");
            }
            
            if (patternDetector.isTemporalQuery(userQuery)) {
                scores.put("line", scores.getOrDefault("line", 0.0) + 3.0 * KEYWORD_WEIGHT);
                logger.debug("Temporal pattern detected, boosting line chart score");
            }
            
            if (patternDetector.isDistributionQuery(userQuery)) {
                scores.put("pie", scores.getOrDefault("pie", 0.0) + 3.0 * KEYWORD_WEIGHT);
                logger.debug("Distribution pattern detected, boosting pie chart score");
            }
            
            if (patternDetector.isComparisonQuery(userQuery)) {
                scores.put("bar", scores.getOrDefault("bar", 0.0) + 2.0 * KEYWORD_WEIGHT);
                logger.debug("Comparison pattern detected, boosting bar chart score");
            }
        }
        
        // 5. Keyword matching (low priority, only if no explicit request)
        if (!hasExplicitRequest) {
            String queryLower = userQuery.toLowerCase(Locale.ENGLISH);
            scores.put("line", scores.getOrDefault("line", 0.0) + 
                      calculateScore(queryLower, TREND_KEYWORDS) * KEYWORD_WEIGHT);
            scores.put("bar", scores.getOrDefault("bar", 0.0) + 
                      calculateScore(queryLower, COMPARISON_KEYWORDS) * KEYWORD_WEIGHT);
            scores.put("pie", scores.getOrDefault("pie", 0.0) + 
                      calculateScore(queryLower, DISTRIBUTION_KEYWORDS) * KEYWORD_WEIGHT);
            scores.put("bubble", scores.getOrDefault("bubble", 0.0) + 
                      calculateScore(queryLower, CORRELATION_KEYWORDS) * KEYWORD_WEIGHT);
        }
        
        // Determine primary intent
        String primaryIntent = determinePrimaryIntent(scores);
        
        // Calculate confidence (0-1)
        double maxScore = scores.values().stream().max(Double::compareTo).orElse(0.0);
        double totalScore = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double confidence = totalScore > 0 ? Math.min(1.0, maxScore / Math.max(1.0, totalScore / scores.size())) : 0.5;
        
        logger.info("Query analyzed: query='{}', primaryIntent={}, confidence={}, scores={}", 
                userQuery.length() > 50 ? userQuery.substring(0, 50) + "..." : userQuery,
                primaryIntent, String.format("%.2f", confidence), scores);
        
        return new QueryIntent(scores, primaryIntent, hasExplicitRequest,
                              explicitChartType, sqlAnalysis, confidence);
    }
    
    /**
     * Detects explicit chart type requests in user query.
     */
    private Optional<String> detectExplicitChartType(String query) {
        Matcher matcher = EXPLICIT_CHART_PATTERN.matcher(query);
        if (matcher.find()) {
            String chartType = matcher.group(2).toLowerCase();
            // Normalize chart type names
            if (chartType.equals("chart") || chartType.equals("graph")) {
                // Try to infer from context - look for more specific terms nearby
                String context = query.substring(Math.max(0, matcher.start() - 20), 
                                                 Math.min(query.length(), matcher.end() + 20)).toLowerCase();
                if (context.contains("line")) return Optional.of("line");
                if (context.contains("bar")) return Optional.of("bar");
                if (context.contains("pie")) return Optional.of("pie");
                if (context.contains("bubble")) return Optional.of("bubble");
                if (context.contains("table")) return Optional.of("table");
            } else {
                return Optional.of(chartType);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Analyzes BigQuery result structure to suggest chart types.
     */
    private Map<String, Double> analyzeResultStructure(BigQueryResult result, ResultStatsSummary statsSummary) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 0.0);
        scores.put("bar", 0.0);
        scores.put("pie", 0.0);
        scores.put("bubble", 0.0);
        scores.put("table", 0.0);
        
        int rowCount = result.getRowCount();
        int columnCount = result.getColumnCount();
        List<String> numericColumns = result.getNumericColumns();
        List<String> categoricalColumns = result.getCategoricalColumns();

        if (rowCount >= 1 && rowCount <= 10) {
            scores.put("pie", scores.getOrDefault("pie", 0.0) + 3.0);
            scores.put("bar", scores.getOrDefault("bar", 0.0) + 2.0);
        } else if (rowCount > 10 && rowCount <= 50) {
            scores.put("bar", scores.getOrDefault("bar", 0.0) + 3.0);
        } else if (rowCount > 50 && rowCount < 100) {
            scores.put("line", scores.getOrDefault("line", 0.0) + 2.0);
            scores.put("table", scores.getOrDefault("table", 0.0) + 1.0);
        } else if (rowCount >= 200) {
            scores.put("table", scores.getOrDefault("table", 0.0) + 5.0);
        }

        if (statsSummary != null) {
            if (statsSummary.hasTemporalDimension() && rowCount >= 4 && rowCount <= 400) {
                scores.put("line", scores.getOrDefault("line", 0.0) + 6.0);
            }

            int dimensionCardinality = statsSummary.getPrimaryDimensionCardinality();
            if (dimensionCardinality > 0 && dimensionCardinality <= 15 && statsSummary.hasCategoryAndMetric()) {
                scores.put("bar", scores.getOrDefault("bar", 0.0) + 4.0);
            }

            if (dimensionCardinality > 0 && dimensionCardinality <= 6
                    && statsSummary.isPrimaryMetricNonNegative()
                    && rowCount <= 12) {
                scores.put("pie", scores.getOrDefault("pie", 0.0) + 4.0);
            }

            if (dimensionCardinality > 20 || statsSummary.getRowCount() > 200 || columnCount > 6) {
                scores.put("table", scores.getOrDefault("table", 0.0) + 5.0);
            }
        }

        if (numericColumns.size() >= 3 && categoricalColumns.size() >= 1) {
            scores.put("bubble", scores.getOrDefault("bubble", 0.0) + 3.0);
        } else if (numericColumns.size() >= 2 && categoricalColumns.size() >= 1) {
            scores.put("bubble", scores.getOrDefault("bubble", 0.0) + 1.0);
        }

        if (columnCount > 5) {
            scores.put("table", scores.getOrDefault("table", 0.0) + 2.0);
        }

        return scores;
    }
    
    private double calculateScore(String query, String[] keywords) {
        double score = 0.0;
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                // Longer keywords get higher weight
                score += 1.0 + (keyword.length() / 10.0);
            }
        }
        return score;
    }
    
    private String determinePrimaryIntent(Map<String, Double> scores) {
        String maxType = "table";
        double maxScore = scores.getOrDefault("table", 0.0);
        
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() > maxScore) {
                maxScore = entry.getValue();
                maxType = entry.getKey();
            }
        }
        
        // Map chart type to intent
        switch (maxType) {
            case "line":
                return "trend";
            case "bar":
                return "comparison";
            case "pie":
                return "distribution";
            case "bubble":
                return "correlation";
            default:
                return "table";
        }
    }
    
    private QueryIntent createDefaultIntent() {
        Map<String, Double> defaultScores = new HashMap<>();
        defaultScores.put("table", 1.0);
        defaultScores.put("bar", 0.0);
        defaultScores.put("line", 0.0);
        defaultScores.put("pie", 0.0);
        defaultScores.put("bubble", 0.0);
        return new QueryIntent(defaultScores, "table");
    }
}

