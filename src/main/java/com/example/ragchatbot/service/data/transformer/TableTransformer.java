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
 * Transforms BigQuery results into table format.
 * Best for: complex data, many columns, detailed information, fallback option.
 */
@Component
public class TableTransformer implements ResultTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(TableTransformer.class);
    
    @Override
    public boolean canTransform(BigQueryResult result, QueryIntent intent) {
        // Table transformer can always handle any result as fallback
        return true;
    }
    
    @Override
    public TransformedData transform(BigQueryResult result) {
        logger.debug("Transforming data for table: rowCount={}, columnCount={}", 
                result.getRowCount(), result.getColumnCount());
        
        TransformedData transformedData = new TransformedData("table");
        
        // Get column names
        List<String> columnNames = result.getColumnNames();
        
        // Get all rows
        List<List<Object>> rows = result.getAllRows();
        
        // Format rows to ensure consistent column count
        List<List<Object>> formattedRows = new ArrayList<>();
        for (List<Object> row : rows) {
            List<Object> formattedRow = new ArrayList<>();
            for (int i = 0; i < columnNames.size(); i++) {
                if (i < row.size()) {
                    formattedRow.add(row.get(i));
                } else {
                    formattedRow.add(null);
                }
            }
            formattedRows.add(formattedRow);
        }
        
        transformedData.put("columns", columnNames);
        transformedData.put("rows", formattedRows);
        
        return transformedData;
    }
    
    @Override
    public String getTargetChartType() {
        return "table";
    }
    
    @Override
    public int getPriority() {
        return 10; // Lowest priority - always available as fallback
    }
}

