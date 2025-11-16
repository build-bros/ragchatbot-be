package com.example.ragchatbot.service.analysis;

import com.example.ragchatbot.service.analysis.ResultStatsSummary.CategoryMetricValue;
import com.example.ragchatbot.service.data.BigQueryResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Builds lightweight statistical summaries from BigQuery results for downstream
 * chart selection, response templating, and logging.
 */
@Component
public class ResultStatsCollector {

    private static final Set<String> TEMPORAL_KEYWORDS = Set.of(
            "date", "time", "year", "season", "month", "day", "week", "period");

    private static final int MAX_SAMPLE_ROWS = 500;

    public ResultStatsSummary summarize(BigQueryResult result) {
        if (result == null) {
            return ResultStatsSummary.empty();
        }

        List<String> columnNames = result.getColumnNames();
        List<String> numericColumns = result.getNumericColumns();
        List<String> categoricalColumns = result.getCategoricalColumns();
        int rowCount = result.getRowCount();
        int columnCount = result.getColumnCount();
        boolean hasTemporalColumn = hasTemporalColumn(columnNames);
        String rowCountBucket = bucketRowCount(rowCount);

        // Prefer categorical dimension; if none, fall back to temporal column (e.g., season, year)
        String primaryDimension = categoricalColumns.isEmpty()
                ? findFirstTemporalColumn(columnNames)
                : categoricalColumns.get(0);

        // Prefer a non-temporal numeric metric; fall back to first numeric if all are temporal
        String primaryMetric = findPrimaryMetric(numericColumns);

        int primaryDimensionIndex = primaryDimension != null ? result.getColumnIndex(primaryDimension) : -1;
        int primaryMetricIndex = primaryMetric != null ? result.getColumnIndex(primaryMetric) : -1;

        List<List<Object>> rows = result.getAllRows();
        int sampleSize = Math.min(rows.size(), MAX_SAMPLE_ROWS);

        Set<String> distinctDimensionValues = new LinkedHashSet<>();
        Map<String, Integer> categoryFrequency = new HashMap<>();

        Double metricMin = null;
        Double metricMax = null;
        Double metricSum = null;
        int metricCount = 0;
        boolean metricNonNegative = true;

        List<CategoryMetricValue> topMetricRows = new ArrayList<>();
        List<CategoryMetricValue> bottomMetricRows = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            List<Object> row = rows.get(i);

            String categoryValue = null;
            if (primaryDimensionIndex >= 0 && primaryDimensionIndex < row.size()) {
                categoryValue = stringify(row.get(primaryDimensionIndex));
                distinctDimensionValues.add(categoryValue);
                categoryFrequency.merge(categoryValue, 1, (existing, increment) -> existing + increment);
            }

            Double metricValue = null;
            if (primaryMetricIndex >= 0 && primaryMetricIndex < row.size()) {
                metricValue = toDouble(row.get(primaryMetricIndex));
            }

            if (metricValue != null) {
                metricMin = metricMin == null ? metricValue : Math.min(metricMin, metricValue);
                metricMax = metricMax == null ? metricValue : Math.max(metricMax, metricValue);
                metricSum = metricSum == null ? metricValue : metricSum + metricValue;
                metricCount++;
                if (metricValue < 0) {
                    metricNonNegative = false;
                }
                if (categoryValue != null) {
                    trackExtremes(topMetricRows, new CategoryMetricValue(categoryValue, metricValue), true);
                    trackExtremes(bottomMetricRows, new CategoryMetricValue(categoryValue, metricValue), false);
                }
            }
        }

        int dimensionCardinality = distinctDimensionValues.size();
        List<String> topDimensionValues = categoryFrequency.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();

        Double metricAvg = metricCount > 0 && metricSum != null ? metricSum / metricCount : null;

        return new ResultStatsSummary(
                rowCount,
                columnCount,
                numericColumns,
                categoricalColumns,
                hasTemporalColumn,
                rowCountBucket,
                primaryDimension,
                dimensionCardinality,
                topDimensionValues,
                primaryMetric,
                metricMin,
                metricMax,
                metricAvg,
                metricNonNegative,
                topMetricRows,
                bottomMetricRows);
    }

    private void trackExtremes(List<CategoryMetricValue> list, CategoryMetricValue candidate, boolean top) {
        list.add(candidate);
        list.sort((a, b) -> top
                ? Double.compare(b.getMetricValue(), a.getMetricValue())
                : Double.compare(a.getMetricValue(), b.getMetricValue()));
        if (list.size() > 5) {
            list.remove(list.size() - 1);
        }
    }

    private String bucketRowCount(int rowCount) {
        if (rowCount == 0) return "empty";
        if (rowCount <= 5) return "tiny";
        if (rowCount <= 15) return "small";
        if (rowCount <= 50) return "medium";
        if (rowCount <= 200) return "large";
        return "huge";
    }

    private boolean hasTemporalColumn(List<String> columnNames) {
        return findFirstTemporalColumn(columnNames) != null;
    }

    private String findFirstTemporalColumn(List<String> columnNames) {
        for (String name : columnNames) {
            if (isTemporalName(name)) {
                return name;
            }
        }
        return null;
    }

    private boolean isTemporalName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ENGLISH);
        for (String token : TEMPORAL_KEYWORDS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String findPrimaryMetric(List<String> numericColumns) {
        if (numericColumns == null || numericColumns.isEmpty()) {
            return null;
        }

        // First, choose a numeric column that is not temporal
        for (String column : numericColumns) {
            if (!isTemporalName(column)) {
                return column;
            }
        }

        // Fallback: all numeric columns appear temporal, use the first one
        return numericColumns.get(0);
    }

    private String stringify(Object value) {
        return value == null ? "null" : value.toString();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }

        if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}

