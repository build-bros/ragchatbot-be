package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating bubble chart visualizations.
 * Best for: 3-dimensional relationships, correlations between multiple metrics.
 */
@Component
public class BubbleChartStrategy implements VisualizationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(BubbleChartStrategy.class);
    
    @Override
    public boolean canHandle(TransformedData transformedData) {
        // Check if this is bubble chart data
        return "bubble".equals(transformedData.getChartType()) &&
               transformedData.containsKey("x") &&
               transformedData.containsKey("y") &&
               transformedData.containsKey("sizes");
    }
    
    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting bubble chart from transformed data");
        
        Map<String, Object> graphData = new HashMap<>();
        
        // Copy all data from transformed data
        graphData.putAll(transformedData.getData());
        graphData.put("chartType", "bubble");
        
        return graphData;
    }
    
    @Override
    public int getPriority() {
        return 20; // Lower priority - more specific use case
    }
    
    @Override
    public String getChartType() {
        return "bubble";
    }
}

