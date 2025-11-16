package com.example.ragchatbot.service.visualization;

import com.example.ragchatbot.service.data.TransformedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Strategy for creating multi-series line chart visualizations.
 * Best for: comparing multiple entities across the same timeline.
 */
@Component
public class MultiLineChartStrategy implements VisualizationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MultiLineChartStrategy.class);

    @Override
    public boolean canHandle(TransformedData transformedData) {
        return "multi_line".equals(transformedData.getChartType())
                && transformedData.containsKey("series");
    }

    @Override
    public Map<String, Object> format(TransformedData transformedData) {
        logger.debug("Formatting multi-series line chart from transformed data");

        Map<String, Object> graphData = new HashMap<>();
        graphData.putAll(transformedData.getData());
        graphData.put("chartType", "multi_line");
        return graphData;
    }

    @Override
    public int getPriority() {
        return 45; // Slightly higher priority than single-series line
    }

    @Override
    public String getChartType() {
        return "multi_line";
    }
}


