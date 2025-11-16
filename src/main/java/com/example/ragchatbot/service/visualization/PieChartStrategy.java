package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating pie chart visualizations.
 * Best for: part-to-whole relationships, distributions, percentages.
 */
@Component
public class PieChartStrategy implements VisualizationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(PieChartStrategy.class);
    
    @Override
    public boolean canHandle(TransformedData transformedData) {
        // Check if this is pie chart data
        return "pie".equals(transformedData.getChartType()) &&
               transformedData.containsKey("labels") &&
               transformedData.containsKey("values");
    }
    
    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting pie chart from transformed data");
        
        Map<String, Object> graphData = new HashMap<>();
        
        // Copy all data from transformed data
        graphData.putAll(transformedData.getData());
        graphData.put("chartType", "pie");
        
        return graphData;
    }
    
    @Override
    public int getPriority() {
        return 50; // High priority for distribution queries
    }
    
    @Override
    public String getChartType() {
        return "pie";
    }
}

