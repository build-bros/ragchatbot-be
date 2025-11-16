package com.example.ragchatbot.service.visualization;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Utility class to detect common query patterns for chart selection.
 */
@Component
public class QueryPatternDetector {
    
    private static final Pattern RANKING_PATTERN = Pattern.compile(
        "\\b(top|bottom)\\s+\\d+", Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern TEMPORAL_PATTERN = Pattern.compile(
        ".*(over (time|years|seasons)|by (year|season|month)|from \\d+ to \\d+).*", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern DISTRIBUTION_PATTERN = Pattern.compile(
        ".*(distribution|breakdown|percentage|proportion|share) of.*", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
        ".*(compar|versus|vs\\.?).*", 
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Detects if query is a ranking query (top/bottom/highest/lowest).
     */
    public boolean isRankingQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String lower = query.toLowerCase();
        
        // Check for "top N" or "bottom N" patterns
        if (RANKING_PATTERN.matcher(query).find()) {
            return true;
        }
        
        // Check for ranking keywords
        return lower.matches(".*\\b(highest|lowest|best|worst|leader|rank|first|last)\\b.*");
    }
    
    /**
     * Detects if query is a temporal query (over time, by season, etc.).
     */
    public boolean isTemporalQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        // Check for temporal patterns
        if (TEMPORAL_PATTERN.matcher(query).find()) {
            return true;
        }
        
        // Check for explicit temporal keywords
        String lower = query.toLowerCase();
        return lower.matches(".*\\b(trend|progression|across seasons|throughout|timeline|history|historical)\\b.*");
    }
    
    /**
     * Detects if query is a distribution query (distribution of, breakdown, percentage).
     */
    public boolean isDistributionQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        return DISTRIBUTION_PATTERN.matcher(query).find() ||
               query.toLowerCase().matches(".*\\b(split between|how .* divided|composition of)\\b.*");
    }
    
    /**
     * Detects if query is a comparison query (compare, versus, vs).
     */
    public boolean isComparisonQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        return COMPARISON_PATTERN.matcher(query).find();
    }
}

