package com.example.ragchatbot.service.visualization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Contains the results of SQL pattern analysis for visualization intent detection.
 */
public class SqlAnalysisResult {
    private final boolean hasTemporalGrouping;
    private final boolean hasAggregation;
    private final int groupByColumnCount;
    private final boolean hasLimit;
    private final List<String> groupByColumns;
    private final Map<String, Double> chartTypeScores;
    
    public SqlAnalysisResult(boolean hasTemporalGrouping, boolean hasAggregation,
                           int groupByColumnCount, boolean hasLimit,
                           List<String> groupByColumns) {
        this.hasTemporalGrouping = hasTemporalGrouping;
        this.hasAggregation = hasAggregation;
        this.groupByColumnCount = groupByColumnCount;
        this.hasLimit = hasLimit;
        this.groupByColumns = groupByColumns != null ? new ArrayList<>(groupByColumns) : new ArrayList<>();
        this.chartTypeScores = new HashMap<>();
        calculateScores();
    }
    
    private void calculateScores() {
        // Temporal grouping strongly suggests line chart
        if (hasTemporalGrouping) {
            chartTypeScores.put("line", 10.0);
        }
        
        // Aggregation with grouping suggests bar or pie
        if (hasAggregation && groupByColumnCount > 0) {
            if (groupByColumnCount <= 2 && groupByColumnCount > 0) {
                // Few groups suggest pie chart
                chartTypeScores.put("pie", 8.0);
            }
            // Multiple groups suggest bar chart
            chartTypeScores.put("bar", 10.0);
        }
        
        // LIMIT with ORDER BY suggests ranking/bar chart
        if (hasLimit) {
            chartTypeScores.put("bar", chartTypeScores.getOrDefault("bar", 0.0) + 10.0);
        }
        
        // No grouping suggests table
        if (!hasAggregation && groupByColumnCount == 0) {
            chartTypeScores.put("table", 5.0);
        }
    }
    
    public boolean hasTemporalGrouping() {
        return hasTemporalGrouping;
    }
    
    public boolean hasAggregation() {
        return hasAggregation;
    }
    
    public int getGroupByColumnCount() {
        return groupByColumnCount;
    }
    
    public boolean hasLimit() {
        return hasLimit;
    }
    
    public List<String> getGroupByColumns() {
        return new ArrayList<>(groupByColumns);
    }
    
    public Map<String, Double> getChartTypeScores() {
        return new HashMap<>(chartTypeScores);
    }
    
    public double getScore(String chartType) {
        return chartTypeScores.getOrDefault(chartType, 0.0);
    }
}

