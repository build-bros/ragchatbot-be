package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating bar chart visualizations.
 * Best for: categorical comparisons, rankings, top/bottom lists.
 */
@Component
public class BarChartStrategy implements VisualizationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(BarChartStrategy.class);
    
    @Override
    public boolean canHandle(TransformedData transformedData) {
        // Check if this is bar chart data
        return "bar".equals(transformedData.getChartType()) &&
               transformedData.containsKey("x") &&
               transformedData.containsKey("y");
    }
    
    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting bar chart from transformed data");
        
        Map<String, Object> graphData = new HashMap<>();
        
        // Copy all data from transformed data
        graphData.putAll(transformedData.getData());
        graphData.put("chartType", "bar");
        
        return graphData;
    }
    
    @Override
    public int getPriority() {
        return 30; // Medium-high priority
    }
    
    @Override
    public String getChartType() {
        return "bar";
    }
}

