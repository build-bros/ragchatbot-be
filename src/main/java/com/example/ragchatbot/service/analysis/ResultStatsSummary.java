package com.example.ragchatbot.service.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of structural statistics derived from a BigQuery result set.
 * Provides both typed getters for runtime decisions and a map view for logging.
 */
public class ResultStatsSummary {

    public static class CategoryMetricValue {
        private final String category;
        private final Double metricValue;

        public CategoryMetricValue(String category, Double metricValue) {
            this.category = category;
            this.metricValue = metricValue;
        }

        public String getCategory() {
            return category;
        }

        public Double getMetricValue() {
            return metricValue;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("category", category);
            map.put("value", metricValue);
            return map;
        }
    }

    private final int rowCount;
    private final int columnCount;
    private final List<String> numericColumns;
    private final List<String> categoricalColumns;
    private final boolean hasTemporalDimension;
    private final String rowCountBucket;
    private final String primaryDimension;
    private final int primaryDimensionCardinality;
    private final List<String> topDimensionValues;
    private final String primaryMetric;
    private final Double primaryMetricMin;
    private final Double primaryMetricMax;
    private final Double primaryMetricAvg;
    private final boolean primaryMetricNonNegative;
    private final List<CategoryMetricValue> topMetricRows;
    private final List<CategoryMetricValue> bottomMetricRows;

    public ResultStatsSummary(
            int rowCount,
            int columnCount,
            List<String> numericColumns,
            List<String> categoricalColumns,
            boolean hasTemporalDimension,
            String rowCountBucket,
            String primaryDimension,
            int primaryDimensionCardinality,
            List<String> topDimensionValues,
            String primaryMetric,
            Double primaryMetricMin,
            Double primaryMetricMax,
            Double primaryMetricAvg,
            boolean primaryMetricNonNegative,
            List<CategoryMetricValue> topMetricRows,
            List<CategoryMetricValue> bottomMetricRows) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.numericColumns = numericColumns != null ? new ArrayList<>(numericColumns) : new ArrayList<>();
        this.categoricalColumns = categoricalColumns != null ? new ArrayList<>(categoricalColumns) : new ArrayList<>();
        this.hasTemporalDimension = hasTemporalDimension;
        this.rowCountBucket = rowCountBucket;
        this.primaryDimension = primaryDimension;
        this.primaryDimensionCardinality = primaryDimensionCardinality;
        this.topDimensionValues = topDimensionValues != null ? new ArrayList<>(topDimensionValues) : new ArrayList<>();
        this.primaryMetric = primaryMetric;
        this.primaryMetricMin = primaryMetricMin;
        this.primaryMetricMax = primaryMetricMax;
        this.primaryMetricAvg = primaryMetricAvg;
        this.primaryMetricNonNegative = primaryMetricNonNegative;
        this.topMetricRows = topMetricRows != null ? new ArrayList<>(topMetricRows) : new ArrayList<>();
        this.bottomMetricRows = bottomMetricRows != null ? new ArrayList<>(bottomMetricRows) : new ArrayList<>();
    }

    public static ResultStatsSummary empty() {
        return new ResultStatsSummary(
                0,
                0,
                new ArrayList<>(),
                new ArrayList<>(),
                false,
                "empty",
                null,
                0,
                new ArrayList<>(),
                null,
                null,
                null,
                null,
                true,
                new ArrayList<>(),
                new ArrayList<>());
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public List<String> getNumericColumns() {
        return new ArrayList<>(numericColumns);
    }

    public List<String> getCategoricalColumns() {
        return new ArrayList<>(categoricalColumns);
    }

    public boolean hasTemporalDimension() {
        return hasTemporalDimension;
    }

    public String getRowCountBucket() {
        return rowCountBucket;
    }

    public String getPrimaryDimension() {
        return primaryDimension;
    }

    public int getPrimaryDimensionCardinality() {
        return primaryDimensionCardinality;
    }

    public List<String> getTopDimensionValues() {
        return new ArrayList<>(topDimensionValues);
    }

    public String getPrimaryMetric() {
        return primaryMetric;
    }

    public Double getPrimaryMetricMin() {
        return primaryMetricMin;
    }

    public Double getPrimaryMetricMax() {
        return primaryMetricMax;
    }

    public Double getPrimaryMetricAvg() {
        return primaryMetricAvg;
    }

    public boolean isPrimaryMetricNonNegative() {
        return primaryMetricNonNegative;
    }

    public List<CategoryMetricValue> getTopMetricRows() {
        return new ArrayList<>(topMetricRows);
    }

    public List<CategoryMetricValue> getBottomMetricRows() {
        return new ArrayList<>(bottomMetricRows);
    }

    public boolean hasCategoryAndMetric() {
        return primaryDimension != null && primaryMetric != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("rowCount", rowCount);
        map.put("columnCount", columnCount);
        map.put("rowCountBucket", rowCountBucket);
        map.put("numericColumns", numericColumns);
        map.put("categoricalColumns", categoricalColumns);
        map.put("hasTemporalDimension", hasTemporalDimension);
        map.put("primaryDimension", primaryDimension);
        map.put("primaryDimensionCardinality", primaryDimensionCardinality);
        map.put("topDimensionValues", topDimensionValues);
        map.put("primaryMetric", primaryMetric);
        map.put("primaryMetricMin", primaryMetricMin);
        map.put("primaryMetricMax", primaryMetricMax);
        map.put("primaryMetricAvg", primaryMetricAvg);
        map.put("primaryMetricNonNegative", primaryMetricNonNegative);
        map.put("topMetricRows", convertCategoryMetric(topMetricRows));
        map.put("bottomMetricRows", convertCategoryMetric(bottomMetricRows));
        return map;
    }

    private List<Map<String, Object>> convertCategoryMetric(List<CategoryMetricValue> list) {
        List<Map<String, Object>> converted = new ArrayList<>();
        if (list == null) {
            return converted;
        }
        for (CategoryMetricValue value : list) {
            converted.add(value.toMap());
        }
        return converted;
    }
}

