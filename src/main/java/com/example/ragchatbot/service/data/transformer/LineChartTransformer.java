package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Transforms BigQuery results into optimized data structure for line charts.
 * Best for: trends over time, sequential data, continuous progressions.
 */
@Component
public class LineChartTransformer implements ResultTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(LineChartTransformer.class);
    
    @Override
    public boolean canTransform(BigQueryResult result, QueryIntent intent) {
        // Need at least 2 columns and some rows
        if (result.getColumnCount() < 2 || result.getRowCount() < 2) {
            return false;
        }
        
        // Must have temporal signal
        boolean hasStrongLineIntent = intent.getScore("line") > 1.5 || 
                                      intent.getPrimaryIntent().equals("trend");
        
        if (hasStrongLineIntent) {
            // Data structure check:
            // - Has temporal column (date, season, year)
            // - At least one numeric metric
            // - Reasonable number of data points
            boolean hasTemporal = findTemporalColumnIndex(result) >= 0;
            boolean hasNumeric = !result.getNumericColumns().isEmpty();
            boolean reasonableRowCount = result.getRowCount() >= 3 && result.getRowCount() <= 100;
            
            if (hasTemporal && hasNumeric && reasonableRowCount) {
                logger.debug("LineChartTransformer: Strong intent match - score={}, intent={}, rows={}, hasTemporal={}", 
                        intent.getScore("line"), intent.getPrimaryIntent(), result.getRowCount(), hasTemporal);
                return true;
            }
        }
        
        // Fallback: moderate line intent with temporal column
        if (intent.getScore("line") > 0.5) {
            boolean hasTemporal = findTemporalColumnIndex(result) >= 0;
            boolean hasNumeric = !result.getNumericColumns().isEmpty();
            
            if (hasTemporal && hasNumeric && result.getRowCount() >= 3) {
                logger.debug("LineChartTransformer: Moderate intent match - score={}, rows={}, hasTemporal={}", 
                        intent.getScore("line"), result.getRowCount(), hasTemporal);
                return true;
            }
        }
        
        return false;
    }
    
    private int findTemporalColumnIndex(BigQueryResult result) {
        List<String> columnNames = result.getColumnNames();
        for (int i = 0; i < columnNames.size(); i++) {
            if (isTemporalName(columnNames.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isTemporalName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ENGLISH);
        return lower.contains("date") ||
               lower.contains("time") ||
               lower.contains("season") ||
               lower.contains("year") ||
               lower.contains("month") ||
               lower.contains("day") ||
               lower.contains("period");
    }

    @Override
    public TransformedData transform(BigQueryResult result) {
        logger.debug("Transforming data for line chart: rowCount={}, columnCount={}", 
                result.getRowCount(), result.getColumnCount());
        
        List<List<Object>> rows = result.getAllRows();

        // Prefer temporal column for x-axis when available
        int xColumnIndex = findTemporalColumnIndex(result);
        if (xColumnIndex == -1) {
            // Fallback to first column
            xColumnIndex = 0;
        }

        // Identify potential series/category column (non-temporal, non-metric categorical column)
        int seriesColumnIndex = findSeriesColumnIndex(result, xColumnIndex);

        // Find numeric column for y-axis that is not the temporal x-axis or series column
        int yColumnIndex = findMetricColumnIndex(result, xColumnIndex, seriesColumnIndex);

        boolean hasSeries = seriesColumnIndex >= 0;
        TransformedData transformedData = new TransformedData(hasSeries ? "multi_line" : "line");

        // If x and y still collide on the same numeric column, use index for x-axis
        if (yColumnIndex == xColumnIndex && yColumnIndex >= 0 && result.isNumericColumn(yColumnIndex)) {
            xColumnIndex = -1;
        }

        // If we have a series column, build multi-series data structure
        if (hasSeries) {
            Map<String, SeriesData> seriesMap = new LinkedHashMap<>();

            for (List<Object> row : rows) {
                String seriesName = safeString(row, seriesColumnIndex, "Series " + (seriesMap.size() + 1));
                SeriesData data = seriesMap.computeIfAbsent(seriesName, name -> new SeriesData());

                Object xValue = extractXValue(row, xColumnIndex, data.x.size());
                double yValue = extractNumericValue(row, yColumnIndex);

                data.x.add(xValue);
                data.y.add(yValue);
                data.labels.add(buildLabel(row, result));
            }

            // Populate series array
            List<Map<String, Object>> seriesList = new ArrayList<>();
            for (Map.Entry<String, SeriesData> entry : seriesMap.entrySet()) {
                Map<String, Object> series = new HashMap<>();
                series.put("name", entry.getKey());
                series.put("x", entry.getValue().x);
                series.put("y", entry.getValue().y);
                series.put("labels", entry.getValue().labels);
                seriesList.add(series);
            }

            transformedData.put("series", seriesList);
        } else {
            // Single-series fallback (current behavior)
            List<Object> xData = new ArrayList<>();
            List<Object> yData = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (List<Object> row : rows) {
                xData.add(extractXValue(row, xColumnIndex, xData.size()));
                yData.add(extractNumericValue(row, yColumnIndex));
                labels.add(buildLabel(row, result));
            }

            transformedData.put("x", xData);
            transformedData.put("y", yData);
            transformedData.put("labels", labels);
        }

        transformedData.put("xLabel", xColumnIndex >= 0 ? result.getColumnNames().get(xColumnIndex) : "Index");
        transformedData.put("yLabel", yColumnIndex >= 0 ? result.getColumnNames().get(yColumnIndex) : "Value");
        
        return transformedData;
    }
    
    @Override
    public String getTargetChartType() {
        return "line";
    }
    
    @Override
    public int getPriority() {
        return 40; // High priority for trend queries
    }

    private static final List<String> SERIES_KEYWORDS = List.of(
            "team", "player", "name", "label", "category", "group", "exchange", "conference", "series"
    );

    private int findSeriesColumnIndex(BigQueryResult result, int xColumnIndex) {
        List<String> columnNames = result.getColumnNames();
        for (int i = 0; i < columnNames.size(); i++) {
            if (i == xColumnIndex) {
                continue;
            }
            String name = columnNames.get(i);
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ENGLISH);
            boolean keywordMatch = SERIES_KEYWORDS.stream().anyMatch(lower::contains);
            boolean isNumeric = result.isNumericColumn(i);
            if (keywordMatch || (!isNumeric && !isTemporalName(name))) {
                return i;
            }
        }
        return -1;
    }

    private int findMetricColumnIndex(BigQueryResult result, int xColumnIndex, int seriesColumnIndex) {
        List<String> numericColumns = result.getNumericColumns();
        for (String numericColumn : numericColumns) {
            Integer idx = result.getColumnIndex(numericColumn);
            if (idx != null && idx >= 0 && idx != xColumnIndex && idx != seriesColumnIndex && !isTemporalName(numericColumn)) {
                return idx;
            }
        }

        for (int i = 0; i < result.getColumnCount(); i++) {
            if (result.isNumericColumn(i) && i != xColumnIndex && i != seriesColumnIndex) {
                return i;
            }
        }

        return result.getFirstNumericColumnIndex();
    }

    private Object extractXValue(List<Object> row, int xColumnIndex, int fallbackIndex) {
        if (xColumnIndex >= 0 && xColumnIndex < row.size()) {
            return row.get(xColumnIndex);
        }
        return fallbackIndex + 1;
    }

    private double extractNumericValue(List<Object> row, int yColumnIndex) {
        if (yColumnIndex >= 0 && yColumnIndex < row.size()) {
            Object value = row.get(yColumnIndex);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value != null) {
                try {
                    return Double.parseDouble(value.toString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0.0;
    }

    private String buildLabel(List<Object> row, BigQueryResult result) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < Math.min(3, result.getColumnCount()); i++) {
            if (i < row.size() && row.get(i) != null) {
                if (label.length() > 0) label.append(" - ");
                label.append(result.getColumnNames().get(i))
                      .append(": ")
                      .append(row.get(i));
            }
        }
        return label.toString();
    }

    private String safeString(List<Object> row, int index, String fallback) {
        if (index >= 0 && index < row.size() && row.get(index) != null) {
            return row.get(index).toString();
        }
        return fallback;
    }

    private static class SeriesData {
        final List<Object> x = new ArrayList<>();
        final List<Object> y = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
    }
}


