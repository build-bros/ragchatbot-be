package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms BigQuery results into optimized data structure for bubble charts.
 * Best for: 3-dimensional relationships, correlations between multiple metrics.
 */
@Component
public class BubbleChartTransformer implements ResultTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(BubbleChartTransformer.class);
    
    @Override
    public boolean canTransform(BigQueryResult result, QueryIntent intent) {
        // Need at least 3 columns (x, y, size) and some rows
        if (result.getColumnCount() < 3 || result.getRowCount() < 2) {
            return false;
        }
        
        // Check query intent - correlation or multi-metric comparison
        boolean hasBubbleIntent = intent.getScore("bubble") > 0.5 || 
                                  intent.getPrimaryIntent().equals("correlation");
        
        if (hasBubbleIntent) {
            // Need at least 2 numeric columns for x and y
            List<String> numericCols = result.getNumericColumns();
            if (numericCols.size() >= 2 && result.getColumnCount() >= 3) {
                logger.debug("BubbleChartTransformer: Intent match - score={}, intent={}, numericCols={}, totalCols={}", 
                        intent.getScore("bubble"), intent.getPrimaryIntent(), numericCols.size(), result.getColumnCount());
                return true;
            }
        }
        
        // Also check if we have 3+ numeric columns (good for bubble chart)
        List<String> numericCols = result.getNumericColumns();
        if (numericCols.size() >= 3 && result.getRowCount() >= 5) {
            logger.debug("BubbleChartTransformer: Multi-metric data match - numericCols={}, rows={}", 
                    numericCols.size(), result.getRowCount());
            return true;
        }
        
        return false;
    }
    
    @Override
    public TransformedData transform(BigQueryResult result) {
        logger.debug("Transforming data for bubble chart: rowCount={}, columnCount={}", 
                result.getRowCount(), result.getColumnCount());
        
        TransformedData transformedData = new TransformedData("bubble");
        
        List<String> numericCols = result.getNumericColumns();
        
        // Use first numeric column for x-axis
        int xColumnIndex = numericCols.size() > 0 ? result.getColumnIndex(numericCols.get(0)) : 0;
        
        // Use second numeric column for y-axis
        int yColumnIndex = numericCols.size() > 1 ? result.getColumnIndex(numericCols.get(1)) : 
                          (result.getColumnCount() > 1 ? 1 : 0);
        
        // Use third numeric column for bubble size, or first if only 2 numeric columns
        int sizeColumnIndex = numericCols.size() > 2 ? result.getColumnIndex(numericCols.get(2)) : 
                             (numericCols.size() > 0 ? result.getColumnIndex(numericCols.get(0)) : 0);
        
        // Find label column (prefer categorical, fallback to first column)
        int labelColumnIndex = result.getFirstCategoricalColumnIndex();
        if (labelColumnIndex == -1) {
            labelColumnIndex = 0;
        }
        
        List<Object> xData = new ArrayList<>();
        List<Object> yData = new ArrayList<>();
        List<Object> sizes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        
        // Calculate max size for normalization
        List<List<Object>> rows = result.getAllRows();
        double maxSize = 0.0;
        for (List<Object> row : rows) {
            if (sizeColumnIndex < row.size()) {
                Object sizeValue = row.get(sizeColumnIndex);
                double size = 0.0;
                if (sizeValue instanceof Number) {
                    size = ((Number) sizeValue).doubleValue();
                } else if (sizeValue != null) {
                    try {
                        size = Double.parseDouble(sizeValue.toString());
                    } catch (NumberFormatException e) {
                        size = 0.0;
                    }
                }
                maxSize = Math.max(maxSize, Math.abs(size));
            }
        }
        
        for (List<Object> row : rows) {
            // X-axis data
            double xValue = 0.0;
            if (xColumnIndex < row.size()) {
                Object xObj = row.get(xColumnIndex);
                if (xObj instanceof Number) {
                    xValue = ((Number) xObj).doubleValue();
                } else if (xObj != null) {
                    try {
                        xValue = Double.parseDouble(xObj.toString());
                    } catch (NumberFormatException e) {
                        xValue = 0.0;
                    }
                }
            }
            xData.add(xValue);
            
            // Y-axis data
            double yValue = 0.0;
            if (yColumnIndex < row.size()) {
                Object yObj = row.get(yColumnIndex);
                if (yObj instanceof Number) {
                    yValue = ((Number) yObj).doubleValue();
                } else if (yObj != null) {
                    try {
                        yValue = Double.parseDouble(yObj.toString());
                    } catch (NumberFormatException e) {
                        yValue = 0.0;
                    }
                }
            }
            yData.add(yValue);
            
            // Size data (normalized to reasonable range for visualization)
            double size = 0.0;
            if (sizeColumnIndex < row.size()) {
                Object sizeValue = row.get(sizeColumnIndex);
                if (sizeValue instanceof Number) {
                    size = ((Number) sizeValue).doubleValue();
                } else if (sizeValue != null) {
                    try {
                        size = Double.parseDouble(sizeValue.toString());
                    } catch (NumberFormatException e) {
                        size = 0.0;
                    }
                }
            }
            // Normalize to 10-50 range for bubble sizes
            double normalizedSize = maxSize > 0 ? 
                (Math.abs(size) / maxSize) * 40 + 10 : 20;
            sizes.add(normalizedSize);
            
            // Label
            if (labelColumnIndex >= 0 && labelColumnIndex < row.size() && row.get(labelColumnIndex) != null) {
                labels.add(row.get(labelColumnIndex).toString());
            } else {
                labels.add("Item " + (labels.size() + 1));
            }
        }
        
        transformedData.put("x", xData);
        transformedData.put("y", yData);
        transformedData.put("sizes", sizes);
        transformedData.put("labels", labels);
        transformedData.put("xLabel", xColumnIndex < result.getColumnCount() ? 
                      result.getColumnNames().get(xColumnIndex) : "X Value");
        transformedData.put("yLabel", yColumnIndex < result.getColumnCount() ? 
                      result.getColumnNames().get(yColumnIndex) : "Y Value");
        
        return transformedData;
    }
    
    @Override
    public String getTargetChartType() {
        return "bubble";
    }
    
    @Override
    public int getPriority() {
        return 20; // Lower priority - more specific use case
    }
}

