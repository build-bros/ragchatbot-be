package com.example.ragchatbot.service.data;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldValue;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.StandardSQLTypeName;
import com.google.cloud.bigquery.TableResult;

import java.util.*;

/**
 * Rich wrapper around BigQuery TableResult that provides convenient access
 * to column metadata, type information, and efficient data access.
 */
public class BigQueryResult {
    private final TableResult tableResult;
    private final List<String> columnNames;
    private final List<StandardSQLTypeName> columnTypes;
    private final Map<String, Integer> columnIndexMap;
    private final int rowCount;
    private List<List<Object>> cachedRows;

    public BigQueryResult(TableResult tableResult) {
        this.tableResult = tableResult;
        this.columnNames = extractColumnNames(tableResult);
        this.columnTypes = extractColumnTypes(tableResult);
        this.columnIndexMap = buildColumnIndexMap();
        this.rowCount = (int) tableResult.getTotalRows();
    }

    /**
     * Private constructor for creating BigQueryResult from cached data.
     * Used when reconstructing results from cache without a TableResult.
     */
    private BigQueryResult(List<String> columnNames, List<StandardSQLTypeName> columnTypes, List<List<Object>> rows) {
        this.tableResult = null;
        this.columnNames = new ArrayList<>(columnNames);
        this.columnTypes = new ArrayList<>(columnTypes);
        this.columnIndexMap = buildColumnIndexMap();
        this.rowCount = rows != null ? rows.size() : 0;
        this.cachedRows = rows != null ? new ArrayList<>(rows) : new ArrayList<>();
    }

    /**
     * Creates a BigQueryResult from cached data.
     * 
     * @param columnNames List of column names
     * @param columnTypeStrings List of column type strings (e.g., "STRING", "FLOAT64", "INT64")
     * @param rows List of rows, where each row is a List<Object>
     * @return BigQueryResult instance constructed from cached data
     */
    public static BigQueryResult fromCachedData(List<String> columnNames, List<String> columnTypeStrings, List<List<Object>> rows) {
        List<StandardSQLTypeName> columnTypes = convertStringTypesToEnum(columnTypeStrings);
        return new BigQueryResult(columnNames, columnTypes, rows);
    }

    /**
     * Converts string type names to StandardSQLTypeName enum values.
     */
    private static List<StandardSQLTypeName> convertStringTypesToEnum(List<String> typeStrings) {
        List<StandardSQLTypeName> types = new ArrayList<>();
        for (String typeString : typeStrings) {
            StandardSQLTypeName type = convertStringTypeToEnum(typeString);
            types.add(type);
        }
        return types;
    }

    /**
     * Converts a single string type name to StandardSQLTypeName enum.
     */
    private static StandardSQLTypeName convertStringTypeToEnum(String typeString) {
        if (typeString == null) {
            return StandardSQLTypeName.STRING; // Default fallback
        }
        
        try {
            return StandardSQLTypeName.valueOf(typeString.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle common variations or return default
            String upper = typeString.toUpperCase();
            switch (upper) {
                case "STRING":
                    return StandardSQLTypeName.STRING;
                case "INT64":
                case "INTEGER":
                    return StandardSQLTypeName.INT64;
                case "FLOAT64":
                case "FLOAT":
                case "DOUBLE":
                    return StandardSQLTypeName.FLOAT64;
                case "BOOL":
                case "BOOLEAN":
                    return StandardSQLTypeName.BOOL;
                case "DATE":
                    return StandardSQLTypeName.DATE;
                case "TIMESTAMP":
                    return StandardSQLTypeName.TIMESTAMP;
                case "NUMERIC":
                    return StandardSQLTypeName.NUMERIC;
                case "BIGNUMERIC":
                    return StandardSQLTypeName.BIGNUMERIC;
                default:
                    return StandardSQLTypeName.STRING; // Default fallback
            }
        }
    }

    public TableResult getTableResult() {
        return tableResult;
    }

    public List<String> getColumnNames() {
        return new ArrayList<>(columnNames);
    }

    public List<StandardSQLTypeName> getColumnTypes() {
        return new ArrayList<>(columnTypes);
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getColumnCount() {
        return columnNames.size();
    }

    public StandardSQLTypeName getColumnType(String columnName) {
        Integer index = columnIndexMap.get(columnName);
        return index != null && index < columnTypes.size() ? columnTypes.get(index) : null;
    }

    public StandardSQLTypeName getColumnType(int columnIndex) {
        return columnIndex >= 0 && columnIndex < columnTypes.size() ? columnTypes.get(columnIndex) : null;
    }

    public Integer getColumnIndex(String columnName) {
        return columnIndexMap.get(columnName);
    }

    /**
     * Gets all values for a specific column by name.
     */
    public List<Object> getColumn(String columnName) {
        Integer index = columnIndexMap.get(columnName);
        if (index == null) {
            return Collections.emptyList();
        }
        return getColumnByIndex(index);
    }

    /**
     * Gets all values for a specific column by index.
     */
    public List<Object> getColumnByIndex(int columnIndex) {
        if (columnIndex < 0 || columnIndex >= columnNames.size()) {
            return Collections.emptyList();
        }

        List<Object> columnValues = new ArrayList<>();
        
        // If tableResult is null (cached data), use cachedRows
        if (tableResult == null) {
            if (cachedRows == null) {
                return Collections.emptyList();
            }
            for (List<Object> row : cachedRows) {
                if (columnIndex < row.size()) {
                    columnValues.add(row.get(columnIndex));
                }
            }
            return columnValues;
        }
        
        // Otherwise, use tableResult
        for (FieldValueList row : tableResult.iterateAll()) {
            if (columnIndex < row.size()) {
                FieldValue fieldValue = row.get(columnIndex);
                columnValues.add(fieldValue.getValue());
            }
        }
        return columnValues;
    }

    /**
     * Gets a specific row by index.
     */
    public List<Object> getRow(int index) {
        if (cachedRows == null) {
            cacheRows();
        }
        if (index >= 0 && index < cachedRows.size()) {
            return new ArrayList<>(cachedRows.get(index));
        }
        return Collections.emptyList();
    }

    /**
     * Gets all rows as List<List<Object>>.
     */
    public List<List<Object>> getAllRows() {
        if (cachedRows == null) {
            cacheRows();
        }
        return new ArrayList<>(cachedRows);
    }

    /**
     * Checks if a column is numeric based on its type.
     */
    public boolean isNumericColumn(String columnName) {
        StandardSQLTypeName type = getColumnType(columnName);
        return type != null && isNumericType(type);
    }

    public boolean isNumericColumn(int columnIndex) {
        StandardSQLTypeName type = getColumnType(columnIndex);
        return type != null && isNumericType(type);
    }

    /**
     * Gets all numeric column names.
     */
    public List<String> getNumericColumns() {
        List<String> numericColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (isNumericColumn(i)) {
                numericColumns.add(columnNames.get(i));
            }
        }
        return numericColumns;
    }

    /**
     * Gets all categorical (non-numeric) column names.
     */
    public List<String> getCategoricalColumns() {
        List<String> categoricalColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            if (!isNumericColumn(i)) {
                categoricalColumns.add(columnNames.get(i));
            }
        }
        return categoricalColumns;
    }

    /**
     * Gets the first numeric column index, or -1 if none found.
     */
    public int getFirstNumericColumnIndex() {
        for (int i = 0; i < columnTypes.size(); i++) {
            if (isNumericType(columnTypes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gets the first categorical column index, or -1 if none found.
     */
    public int getFirstCategoricalColumnIndex() {
        for (int i = 0; i < columnTypes.size(); i++) {
            if (!isNumericType(columnTypes.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<String> extractColumnNames(TableResult result) {
        List<String> names = new ArrayList<>();
        var schema = result.getSchema();
        if (schema != null && schema.getFields() != null) {
            for (Field field : schema.getFields()) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private List<StandardSQLTypeName> extractColumnTypes(TableResult result) {
        List<StandardSQLTypeName> types = new ArrayList<>();
        var schema = result.getSchema();
        if (schema != null && schema.getFields() != null) {
            for (Field field : schema.getFields()) {
                types.add(field.getType().getStandardType());
            }
        }
        return types;
    }

    private Map<String, Integer> buildColumnIndexMap() {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            map.put(columnNames.get(i), i);
        }
        return map;
    }

    private void cacheRows() {
        // If tableResult is null (cached data), rows are already cached in constructor
        if (tableResult == null) {
            return;
        }
        
        cachedRows = new ArrayList<>();
        for (FieldValueList row : tableResult.iterateAll()) {
            List<Object> rowData = new ArrayList<>();
            for (FieldValue fieldValue : row) {
                rowData.add(fieldValue.getValue());
            }
            cachedRows.add(rowData);
        }
    }

    private boolean isNumericType(StandardSQLTypeName type) {
        return type == StandardSQLTypeName.INT64 ||
               type == StandardSQLTypeName.FLOAT64 ||
               type == StandardSQLTypeName.NUMERIC ||
               type == StandardSQLTypeName.BIGNUMERIC;
    }
}

