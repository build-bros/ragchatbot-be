package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating table visualizations.
 * Best for: complex data, many columns, detailed information, fallback option.
 */
@Component
public class TableStrategy implements VisualizationStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(TableStrategy.class);
    
    @Override
    public boolean canHandle(TransformedData transformedData) {
        // Table strategy can always handle any transformed data as fallback
        return "table".equals(transformedData.getChartType());
    }
    
    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting table from transformed data");
        
        Map<String, Object> tableData = new HashMap<>();
        
        // Copy all data from transformed data
        tableData.putAll(transformedData.getData());
        
        return tableData;
    }
    
    @Override
    public int getPriority() {
        return 10; // Lowest priority - always available as fallback
    }
    
    @Override
    public String getChartType() {
        return "table";
    }
}

