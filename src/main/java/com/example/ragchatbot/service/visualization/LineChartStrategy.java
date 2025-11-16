package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating line chart visualizations.
 * Best for: trends over time, sequential data, continuous progressions.
 */
@Component
public class LineChartStrategy implements VisualizationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LineChartStrategy.class);
    
    @Override
    public boolean canHandle(TransformedData transformedData) {
        // Check if this is line chart data
        return "line".equals(transformedData.getChartType()) &&
               transformedData.containsKey("x") &&
               transformedData.containsKey("y");
    }
    
    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting line chart from transformed data");
        
        Map<String, Object> graphData = new HashMap<>();
        
        // Copy all data from transformed data
        graphData.putAll(transformedData.getData());
        graphData.put("chartType", "line");
        
        return graphData;
    }
    
    @Override
    public int getPriority() {
        return 40; // High priority for trend queries
    }
    
    @Override
    public String getChartType() {
        return "line";
    }
}

