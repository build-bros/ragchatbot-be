package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;

import java.util.Map;

/**
 * Strategy interface for formatting transformed data into frontend visualization formats.
 * Strategies work with pre-transformed data optimized for their chart type.
 */
public interface VisualizationStrategy {
    
    /**
     * Determines if this strategy can handle the given transformed data.
     * 
     * @param transformedData The transformed data optimized for visualization
     * @return true if this strategy can handle the data, false otherwise
     */
    boolean canHandle(TransformedData transformedData);
    
    /**
     * Formats the transformed data into frontend visualization format.
     * 
     * @param transformedData The transformed data optimized for visualization
     * @return A map containing the formatted visualization data for the frontend
     */
    Map<String, Object> format(TransformedData transformedData);
    
    /**
     * Returns the priority of this strategy. Higher priority strategies are evaluated first.
     * Strategies with more specific matching criteria should have higher priority.
     * 
     * @return The priority value (higher = more priority)
     */
    int getPriority();
    
    /**
     * Returns the chart type this strategy produces.
     * 
     * @return The chart type string (e.g., "bar", "line", "pie", "bubble", "table")
     */
    String getChartType();
}

