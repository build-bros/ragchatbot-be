package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.visualization.QueryIntent;

/**
 * Interface for transforming BigQuery results into optimized data structures
 * for specific visualization types.
 */
public interface ResultTransformer {
    
    /**
     * Determines if this transformer can handle the given result and intent.
     * 
     * @param result The BigQuery result to transform
     * @param intent The query intent with visualization preferences
     * @return true if this transformer can handle the result, false otherwise
     */
    boolean canTransform(BigQueryResult result, QueryIntent intent);
    
    /**
     * Transforms the BigQuery result into optimized data for visualization.
     * 
     * @param result The BigQuery result to transform
     * @return TransformedData optimized for the target chart type
     */
    TransformedData transform(BigQueryResult result);
    
    /**
     * Returns the chart type this transformer produces.
     * 
     * @return The chart type string (e.g., "bar", "line", "pie", "bubble", "table")
     */
    String getTargetChartType();
    
    /**
     * Returns the priority of this transformer. Higher priority transformers are evaluated first.
     * 
     * @return The priority value (higher = more priority)
     */
    int getPriority();
}

