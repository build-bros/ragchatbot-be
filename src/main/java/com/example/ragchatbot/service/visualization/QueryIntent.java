package com.example.ragchatbot.service.visualization;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the detected intent from a user query, including visualization preferences
 * and confidence scores for different chart types.
 */
public class QueryIntent {
    private final Map<String, Double> chartTypeScores;
    private final String primaryIntent; // "comparison", "trend", "distribution", "correlation", "table"
    private final boolean hasExplicitRequest;
    private final String explicitChartType;
    private final SqlAnalysisResult sqlAnalysis;
    private final double confidence; // 0-1 confidence in the decision
    
    // Constructor for backward compatibility
    public QueryIntent(Map<String, Double> chartTypeScores, String primaryIntent) {
        this(chartTypeScores, primaryIntent, false, null, null, 0.5);
    }
    
    // Full constructor with all fields
    public QueryIntent(Map<String, Double> chartTypeScores, String primaryIntent,
                      boolean hasExplicitRequest, String explicitChartType,
                      SqlAnalysisResult sqlAnalysis, double confidence) {
        this.chartTypeScores = new HashMap<>(chartTypeScores);
        this.primaryIntent = primaryIntent;
        this.hasExplicitRequest = hasExplicitRequest;
        this.explicitChartType = explicitChartType;
        this.sqlAnalysis = sqlAnalysis;
        this.confidence = confidence;
    }
    
    public Map<String, Double> getChartTypeScores() {
        return new HashMap<>(chartTypeScores);
    }
    
    public String getPrimaryIntent() {
        return primaryIntent;
    }
    
    public double getScore(String chartType) {
        return chartTypeScores.getOrDefault(chartType, 0.0);
    }
    
    public String getPreferredChartType() {
        return chartTypeScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("table");
    }
    
    public boolean hasExplicitRequest() {
        return hasExplicitRequest;
    }
    
    public String getExplicitChartType() {
        return explicitChartType;
    }
    
    public SqlAnalysisResult getSqlAnalysis() {
        return sqlAnalysis;
    }
    
    public double getConfidence() {
        return confidence;
    }
}

