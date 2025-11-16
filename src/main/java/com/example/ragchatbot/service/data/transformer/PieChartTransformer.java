package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Transforms BigQuery results into optimized data structure for pie charts.
 * Best for: part-to-whole relationships, distributions, percentages.
 */
@Component
public class PieChartTransformer implements ResultTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(PieChartTransformer.class);
    private static final int MAX_PIE_SEGMENTS = 10;
    
    @Override
    public boolean canTransform(BigQueryResult result, QueryIntent intent) {
        // Need at least 2 columns
        if (result.getColumnCount() < 2 || result.getRowCount() < 1) {
            return false;
        }
        
        // Only use pie chart if there's STRONG signal for distribution
        // Check query intent - must have explicit distribution intent with high score
        if (intent.getScore("pie") > 2.0) {  // Increased threshold from 0.5 to 2.0
            // Check if data is suitable for pie chart
            List<String> numericCols = result.getNumericColumns();
            if (!numericCols.isEmpty()) {
                int numericColIndex = result.getFirstNumericColumnIndex();
                // Pie charts need positive values and reasonable number of segments
                boolean allPositive = areAllNumericValuesPositive(result, numericColIndex);
                boolean reasonableSegments = result.getRowCount() <= MAX_PIE_SEGMENTS && result.getRowCount() >= 2;
                
                if (allPositive && reasonableSegments) {
                    logger.debug("PieChartTransformer: Strong distribution intent match - score={}, intent={}, rows={}, allPositive={}", 
                            intent.getScore("pie"), intent.getPrimaryIntent(), result.getRowCount(), allPositive);
                    return true;
                } else {
                    logger.debug("PieChartTransformer: Rejected - score={}, rows={}, allPositive={}, reasonableSegments={}", 
                            intent.getScore("pie"), result.getRowCount(), allPositive, reasonableSegments);
                }
            }
        } else {
            logger.debug("PieChartTransformer: Rejected - score too low: {} (required > 2.0)", intent.getScore("pie"));
        }
        
        return false;
    }
    
    @Override
    public TransformedData transform(BigQueryResult result) {
        logger.debug("Transforming data for pie chart: rowCount={}, columnCount={}", 
                result.getRowCount(), result.getColumnCount());
        
        TransformedData transformedData = new TransformedData("pie");
        
        // Find categorical column for labels (prefer first categorical)
        int labelColumnIndex = result.getFirstCategoricalColumnIndex();
        if (labelColumnIndex == -1) {
            labelColumnIndex = 0; // Fallback to first column
        }
        
        // Find numeric column for values
        int valueColumnIndex = result.getFirstNumericColumnIndex();
        if (valueColumnIndex == -1) {
            // If no numeric column, use second column
            valueColumnIndex = result.getColumnCount() > 1 ? 1 : -1;
        }
        
        List<String> labels = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        
        List<List<Object>> rows = result.getAllRows();
        for (List<Object> row : rows) {
            // Label (categorical)
            if (labelColumnIndex >= 0 && labelColumnIndex < row.size()) {
                labels.add(row.get(labelColumnIndex) != null ? 
                          row.get(labelColumnIndex).toString() : "Unknown");
            } else {
                labels.add("Item " + (labels.size() + 1));
            }
            
            // Value (numeric)
            if (valueColumnIndex >= 0 && valueColumnIndex < row.size()) {
                Object value = row.get(valueColumnIndex);
                double numericValue = 0.0;
                if (value instanceof Number) {
                    numericValue = ((Number) value).doubleValue();
                } else if (value != null) {
                    try {
                        numericValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        numericValue = 0.0;
                    }
                }
                // Ensure non-negative for pie chart
                values.add(Math.max(0.0, numericValue));
            } else {
                values.add(0.0);
            }
        }
        
        transformedData.put("labels", labels);
        transformedData.put("values", values);
        transformedData.put("xLabel", labelColumnIndex >= 0 ? result.getColumnNames().get(labelColumnIndex) : "Category");
        transformedData.put("yLabel", valueColumnIndex >= 0 ? result.getColumnNames().get(valueColumnIndex) : "Value");
        
        return transformedData;
    }
    
    private boolean areAllNumericValuesPositive(BigQueryResult result, int columnIndex) {
        if (!result.isNumericColumn(columnIndex)) {
            return false;
        }
        
        List<List<Object>> rows = result.getAllRows();
        for (List<Object> row : rows) {
            if (columnIndex < row.size()) {
                Object value = row.get(columnIndex);
                double numericValue = 0.0;
                if (value instanceof Number) {
                    numericValue = ((Number) value).doubleValue();
                } else if (value != null) {
                    try {
                        numericValue = Double.parseDouble(value.toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
                if (numericValue < 0) {
                    return false;
                }
            }
        }
        return true;
    }
    
    @Override
    public String getTargetChartType() {
        return "pie";
    }
    
    @Override
    public int getPriority() {
        return 25; // Lower priority - only use when explicitly requested
    }
}

