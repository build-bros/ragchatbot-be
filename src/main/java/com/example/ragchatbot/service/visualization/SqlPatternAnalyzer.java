package com.example.ragchatbot.service.visualization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analyzes SQL queries to detect patterns that indicate visualization preferences.
 */
@Component
public class SqlPatternAnalyzer {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlPatternAnalyzer.class);
    
    // Temporal column patterns
    private static final String[] TEMPORAL_COLUMNS = {
        "date", "time", "year", "season", "month", "day", "week",
        "scheduled_date", "game_date", "timestamp", "datetime"
    };
    
    // Aggregation function patterns
    private static final Pattern AGGREGATION_PATTERN = Pattern.compile(
        "\\b(SUM|AVG|COUNT|MAX|MIN|STDDEV|VARIANCE)\\s*\\(",
        Pattern.CASE_INSENSITIVE
    );
    
    // GROUP BY pattern
    private static final Pattern GROUP_BY_PATTERN = Pattern.compile(
        "GROUP\\s+BY\\s+([^\\s(]+(?:\\s*,\\s*[^\\s(]+)*)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    // LIMIT pattern
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
        "\\bLIMIT\\s+(\\d+)",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Analyzes SQL query to detect visualization patterns.
     */
    public SqlAnalysisResult analyze(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return new SqlAnalysisResult(false, false, 0, false, new ArrayList<>());
        }
        
        String sqlUpper = sql.toUpperCase();
        boolean hasAggregation = hasAggregations(sqlUpper);
        List<String> groupByColumns = extractGroupByColumns(sql);
        boolean hasTemporalGrouping = hasTemporalGroupBy(groupByColumns);
        boolean hasLimit = hasLimitClause(sqlUpper);
        int groupByCount = groupByColumns.size();
        
        logger.debug("SQL analysis: hasAggregation={}, groupByCount={}, hasTemporal={}, hasLimit={}",
                hasAggregation, groupByCount, hasTemporalGrouping, hasLimit);
        
        return new SqlAnalysisResult(hasTemporalGrouping, hasAggregation,
                groupByCount, hasLimit, groupByColumns);
    }
    
    private boolean hasAggregations(String sql) {
        return AGGREGATION_PATTERN.matcher(sql).find();
    }
    
    private boolean hasTemporalGroupBy(List<String> groupByColumns) {
        if (groupByColumns.isEmpty()) {
            return false;
        }
        
        String columnsLower = String.join(" ", groupByColumns).toLowerCase();
        for (String temporal : TEMPORAL_COLUMNS) {
            if (columnsLower.contains(temporal)) {
                return true;
            }
        }
        return false;
    }
    
    private List<String> extractGroupByColumns(String sql) {
        List<String> columns = new ArrayList<>();
        Matcher matcher = GROUP_BY_PATTERN.matcher(sql);
        
        if (matcher.find()) {
            String groupByClause = matcher.group(1);
            // Split by comma and clean up column names
            String[] parts = groupByClause.split(",");
            for (String part : parts) {
                // Remove table aliases and clean up
                String column = part.trim()
                    .replaceAll("^[a-zA-Z_][a-zA-Z0-9_]*\\.", "") // Remove table alias
                    .replaceAll("\\s+.*$", ""); // Remove any trailing clauses
                
                if (!column.isEmpty()) {
                    columns.add(column);
                }
            }
        }
        
        return columns;
    }
    
    private boolean hasLimitClause(String sql) {
        return LIMIT_PATTERN.matcher(sql).find();
    }
}

