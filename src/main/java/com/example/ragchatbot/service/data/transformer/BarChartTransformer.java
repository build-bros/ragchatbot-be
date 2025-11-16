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
 * Transforms BigQuery results into optimized data structure for bar charts.
 * Best for: categorical comparisons, rankings, top/bottom lists.
 */
@Component
public class BarChartTransformer implements ResultTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(BarChartTransformer.class);
    
    @Override
    public boolean canTransform(BigQueryResult result, QueryIntent intent) {
        // Need at least 2 columns and some rows
        if (result.getColumnCount() < 2 || result.getRowCount() < 1) {
            return false;
        }
        
        // Strong signal: comparison keywords + ranking patterns
        boolean hasStrongBarIntent = intent.getScore("bar") > 1.5 || 
                                     intent.getPrimaryIntent().equals("comparison");
        
        if (hasStrongBarIntent) {
            // Data structure check: 
            // - 1 categorical + 1-2 numeric columns
            // - 2-50 rows (readable on bar chart)
            // - Has numeric columns for y-axis
            List<String> numericCols = result.getNumericColumns();
            
            boolean hasNumeric = !numericCols.isEmpty();
            boolean reasonableRowCount = result.getRowCount() >= 2 && result.getRowCount() <= 50;
            boolean reasonableColumnCount = result.getColumnCount() >= 2 && result.getColumnCount() <= 4;
            
            if (hasNumeric && reasonableRowCount && reasonableColumnCount) {
                logger.debug("BarChartTransformer: Strong intent match - score={}, intent={}, rows={}, cols={}", 
                        intent.getScore("bar"), intent.getPrimaryIntent(), result.getRowCount(), result.getColumnCount());
                return true;
            }
        }
        
        // Fallback: moderate bar intent with suitable data structure
        if (intent.getScore("bar") > 0.5) {
            List<String> numericCols = result.getNumericColumns();
            if (!numericCols.isEmpty() && result.getRowCount() <= 30) {
                logger.debug("BarChartTransformer: Moderate intent match - score={}, rows={}", 
                        intent.getScore("bar"), result.getRowCount());
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public TransformedData transform(BigQueryResult result) {
        logger.debug("Transforming data for bar chart: rowCount={}, columnCount={}", 
                result.getRowCount(), result.getColumnCount());
        
        TransformedData transformedData = new TransformedData("bar");
        
        // Find categorical column for x-axis (prefer first categorical)
        int xColumnIndex = result.getFirstCategoricalColumnIndex();
        if (xColumnIndex == -1) {
            xColumnIndex = 0; // Fallback to first column
        }
        
        // Find numeric column for y-axis (prefer first numeric)
        int yColumnIndex = result.getFirstNumericColumnIndex();
        if (yColumnIndex == -1) {
            // If no numeric column, use second column or index
            yColumnIndex = result.getColumnCount() > 1 ? 1 : -1;
        }
        
        List<Object> xData = new ArrayList<>();
        List<Object> yData = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        
        List<List<Object>> rows = result.getAllRows();
        for (List<Object> row : rows) {
            // X-axis data (categorical)
            if (xColumnIndex >= 0 && xColumnIndex < row.size()) {
                xData.add(row.get(xColumnIndex));
            } else {
                xData.add(xData.size() + 1); // Use index as fallback
            }
            
            // Y-axis data (numeric)
            if (yColumnIndex >= 0 && yColumnIndex < row.size()) {
                Object yValue = row.get(yColumnIndex);
                if (yValue instanceof Number) {
                    yData.add(yValue);
                } else if (yValue != null) {
                    try {
                        yData.add(Double.parseDouble(yValue.toString()));
                    } catch (NumberFormatException e) {
                        yData.add(0.0);
                    }
                } else {
                    yData.add(0.0);
                }
            } else {
                yData.add(0.0);
            }
            
            // Create label from first few columns
            StringBuilder label = new StringBuilder();
            for (int i = 0; i < Math.min(3, result.getColumnCount()); i++) {
                if (i < row.size() && row.get(i) != null) {
                    if (label.length() > 0) label.append(" - ");
                    label.append(result.getColumnNames().get(i))
                          .append(": ")
                          .append(row.get(i));
                }
            }
            labels.add(label.toString());
        }
        
        transformedData.put("x", xData);
        transformedData.put("y", yData);
        transformedData.put("labels", labels);
        transformedData.put("xLabel", xColumnIndex >= 0 ? result.getColumnNames().get(xColumnIndex) : "Index");
        transformedData.put("yLabel", yColumnIndex >= 0 ? result.getColumnNames().get(yColumnIndex) : "Value");
        
        return transformedData;
    }
    
    @Override
    public String getTargetChartType() {
        return "bar";
    }
    
    @Override
    public int getPriority() {
        return 30; // Medium-high priority
    }
}

