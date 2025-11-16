package com.example.ragchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class BigQuerySchemaService {

    private static final Logger logger = LoggerFactory.getLogger(BigQuerySchemaService.class);

    @Value("${gcp.bigquery.dataset}")
    private String datasetName;

    @Value("${gcp.bigquery.schema}")
    private String schemaName;

    private final ObjectMapper objectMapper;
    private String cachedSchemaContext;

    public BigQuerySchemaService() {
        logger.info("Initializing BigQuery Schema service");
        this.objectMapper = new ObjectMapper();
        logger.info("BigQuery Schema service initialized successfully");
    }

    public String getSchemaContext() {
        if (cachedSchemaContext == null) {
            logger.info("Building schema context: dataset={}, schema={}", datasetName, schemaName);
            long startTime = System.currentTimeMillis();
            cachedSchemaContext = buildSchemaContext();
            long buildTime = System.currentTimeMillis() - startTime;
            logger.info("Schema context built: dataset={}, schema={}, contextLength={}, buildTimeMs={}", 
                    datasetName, schemaName, cachedSchemaContext.length(), buildTime);
        } else {
            logger.debug("Returning cached schema context: contextLength={}", cachedSchemaContext.length());
        }
        return cachedSchemaContext;
    }

    private String buildSchemaContext() {
        StringBuilder schemaContext = new StringBuilder();
        schemaContext.append("BigQuery Dataset: ").append(datasetName).append(".").append(schemaName).append("\n\n");
        schemaContext.append("Available Tables and Schemas:\n\n");

        try {
            logger.debug("Loading schema from table-schema.json");
            ClassPathResource resource = new ClassPathResource("table-schema.json");
            
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode schemaArray = objectMapper.readTree(inputStream);
                logger.debug("Loaded {} column definitions from schema file", schemaArray.size());
                
                // Group columns by table name
                Map<String, List<JsonNode>> tableColumns = new LinkedHashMap<>();
                for (JsonNode column : schemaArray) {
                    String tableName = column.get("table_name").asText();
                    tableColumns.computeIfAbsent(tableName, k -> new ArrayList<>()).add(column);
                }
                
                logger.info("Found {} tables in schema file", tableColumns.size());
                
                // Focus on key NCAA basketball tables (in order of importance)
                String[] keyTables = {
                    "mbb_teams",
                    "mbb_players_games_sr",
                    "mbb_games_sr",
                    "mbb_teams_games_sr",
                    "mbb_historical_teams_games",
                    "mbb_historical_tournament_games",
                    "mbb_historical_teams_seasons",
                    "mbb_pbp_sr",
                    "team_colors",
                    "mascots"
                };
                
                int tablesProcessed = 0;
                for (String tableName : keyTables) {
                    if (tableColumns.containsKey(tableName)) {
                        List<JsonNode> columns = tableColumns.get(tableName);
                        // Sort by ordinal position
                        columns.sort(Comparator.comparingInt(c -> c.get("ordinal_position").asInt()));
                        schemaContext.append(formatTableSchema(tableName, columns));
                        tablesProcessed++;
                        logger.debug("Processed table schema: tableName={}, columnCount={}", 
                                tableName, columns.size());
                    } else {
                        logger.debug("Table not in schema file: tableName={}", tableName);
                    }
                }
                
                logger.info("Processed {} key tables for schema context", tablesProcessed);
            }
        } catch (IOException e) {
            logger.error("Error loading schema from file: error={}", e.getMessage(), e);
            schemaContext.append("Error loading schemas: ").append(e.getMessage());
        } catch (Exception e) {
            logger.error("Error processing schema: error={}", e.getMessage(), e);
            schemaContext.append("Error processing schemas: ").append(e.getMessage());
        }

        return schemaContext.toString();
    }

    private String formatTableSchema(String tableName, List<JsonNode> columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(tableName).append("\n");
        sb.append("Full Reference: `").append(datasetName).append(".").append(schemaName)
          .append(".").append(tableName).append("`\n");
        sb.append("Columns:\n");
        
        for (JsonNode column : columns) {
            String columnName = column.get("column_name").asText();
            String dataType = column.get("data_type").asText();
            String isNullable = column.get("is_nullable").asText();
            
            sb.append("  - ").append(columnName)
              .append(" (").append(dataType);
            
            if ("NO".equals(isNullable)) {
                sb.append(", NOT NULL");
            }
            
            sb.append(")\n");
        }
        
        sb.append("\n");
        return sb.toString();
    }
}

