package com.example.ragchatbot.service.visualization;

import java.util.ArrayList;
import java.util.List;

/**
 * Context class that encapsulates all information needed for visualization strategy selection
 * and data formatting.
 */
public class VisualizationContext {
    private final String userQuery;
    private final List<String> columnNames;
    private final List<List<Object>> rows;
    private final QueryIntent queryIntent;
    
    public VisualizationContext(String userQuery, List<String> columnNames, List<List<Object>> rows, QueryIntent queryIntent) {
        this.userQuery = userQuery;
        this.columnNames = columnNames != null ? new ArrayList<>(columnNames) : new ArrayList<>();
        this.rows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
        this.queryIntent = queryIntent;
    }
    
    public String getUserQuery() {
        return userQuery;
    }
    
    public List<String> getColumnNames() {
        return new ArrayList<>(columnNames);
    }
    
    public List<List<Object>> getRows() {
        return new ArrayList<>(rows);
    }
    
    public QueryIntent getQueryIntent() {
        return queryIntent;
    }
    
    public int getRowCount() {
        return rows.size();
    }
    
    public int getColumnCount() {
        return columnNames.size();
    }
    
    /**
     * Returns indices of columns that are numeric (contain mostly numeric values).
     */
    public List<Integer> getNumericColumnIndices() {
        List<Integer> numericIndices = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (isNumericColumn(i)) {
                numericIndices.add(i);
            }
        }
        return numericIndices;
    }
    
    /**
     * Returns indices of columns that are categorical (non-numeric).
     */
    public List<Integer> getCategoricalColumnIndices() {
        List<Integer> categoricalIndices = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (!isNumericColumn(i)) {
                categoricalIndices.add(i);
            }
        }
        return categoricalIndices;
    }
    
    /**
     * Checks if a column is numeric (more than 50% of values are numeric).
     */
    public boolean isNumericColumn(int columnIndex) {
        if (rows.isEmpty() || columnIndex < 0 || columnIndex >= columnNames.size()) {
            return false;
        }
        
        int numericCount = 0;
        for (List<Object> row : rows) {
            if (columnIndex < row.size()) {
                Object value = row.get(columnIndex);
                if (value instanceof Number) {
                    numericCount++;
                } else if (value != null) {
                    try {
                        Double.parseDouble(value.toString());
                        numericCount++;
                    } catch (NumberFormatException e) {
                        // Not numeric
                    }
                }
            }
        }
        
        return numericCount > rows.size() * 0.5;
    }
    
    /**
     * Gets the first numeric column index, or -1 if none found.
     */
    public int getFirstNumericColumnIndex() {
        List<Integer> numericIndices = getNumericColumnIndices();
        return numericIndices.isEmpty() ? -1 : numericIndices.get(0);
    }
    
    /**
     * Gets the first categorical column index, or -1 if none found.
     */
    public int getFirstCategoricalColumnIndex() {
        List<Integer> categoricalIndices = getCategoricalColumnIndices();
        return categoricalIndices.isEmpty() ? -1 : categoricalIndices.get(0);
    }
    
    /**
     * Gets a numeric value from a row and column, converting if necessary.
     */
    public double getNumericValue(List<Object> row, int columnIndex) {
        if (columnIndex < 0 || columnIndex >= row.size()) {
            return 0.0;
        }
        
        Object value = row.get(columnIndex);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    /**
     * Checks if all numeric values in a column are positive (for pie charts).
     */
    public boolean areAllNumericValuesPositive(int columnIndex) {
        if (!isNumericColumn(columnIndex)) {
            return false;
        }
        
        for (List<Object> row : rows) {
            double value = getNumericValue(row, columnIndex);
            if (value < 0) {
                return false;
            }
        }
        return true;
    }
}

