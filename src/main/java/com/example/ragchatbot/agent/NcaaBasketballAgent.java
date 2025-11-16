package com.example.ragchatbot.agent;

import com.example.ragchatbot.service.BigQuerySchemaService;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NcaaBasketballAgent {

    private static final Logger logger = LoggerFactory.getLogger(NcaaBasketballAgent.class);

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.vertexai.location:us-central1}")
    private String location;

    @Value("${gcp.vertexai.model:gemini-pro}")
    private String modelName;

    @Value("${gcp.bigquery.dataset}")
    private String datasetName;

    @Value("${gcp.bigquery.schema}")
    private String schemaName;

    private final BigQuerySchemaService schemaService;
    private Client genAiClient;

    public NcaaBasketballAgent(BigQuerySchemaService schemaService) {
        this.schemaService = schemaService;
    }

    @PostConstruct
    public void initialize() {
        logger.info("Initializing Vertex AI client: projectId={}, location={}, model={}", 
                projectId, location, modelName);
        try {
            // Initialize Vertex AI client using builder pattern
            // The client will use Application Default Credentials (ADC) for authentication
            this.genAiClient = Client.builder()
                    .project(projectId)
                    .location(location)
                    .vertexAI(true)
                    .build();
            logger.info("Vertex AI client initialized successfully: projectId={}, location={}, model={}", 
                    projectId, location, modelName);
        } catch (Exception e) {
            logger.error("Failed to initialize Vertex AI client: projectId={}, location={}, error={}", 
                    projectId, location, e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Vertex AI client", e);
        }
    }

    public String generateSql(String userQuery) throws IOException, InterruptedException {
        logger.debug("Starting SQL generation: userQueryLength={}", userQuery.length());
        
        long startTime = System.currentTimeMillis();
        String schemaContext = schemaService.getSchemaContext();
        logger.debug("Schema context retrieved: schemaContextLength={}", schemaContext.length());
        
        String systemPrompt = buildSystemPrompt(schemaContext);
        String userPrompt = "User query: " + userQuery + "\n\nGenerate a BigQuery SQL query to answer this question. " +
                           "Return ONLY the SQL query, no explanations or markdown formatting.";

        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        logger.debug("Prompt constructed: systemPromptLength={}, userPromptLength={}, fullPromptLength={}", 
                systemPrompt.length(), userPrompt.length(), fullPrompt.length());
        
        try {
            long apiCallStart = System.currentTimeMillis();
            GenerateContentResponse response = genAiClient.models.generateContent(modelName, fullPrompt, null);
            long apiCallTime = System.currentTimeMillis() - apiCallStart;
            logger.info("Vertex AI API call completed: model={}, apiCallTimeMs={}, responseSize={}", 
                    modelName, apiCallTime, response.text() != null ? response.text().length() : 0);
            
            String sql = extractSqlFromResponse(response);
            logger.debug("SQL extracted from response: sqlLength={}", sql.length());
            
            // Validate SQL is a complete query
            validateSqlCompleteness(sql);
            logger.debug("SQL completeness validation passed: sqlLength={}", sql.length());
            
            // Validate SQL safety
            validateSqlSafety(sql);
            logger.debug("SQL safety validation passed: sqlLength={}", sql.length());
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("SQL generation completed: totalTimeMs={}, apiCallTimeMs={}, sqlLength={}", 
                    totalTime, apiCallTime, sql.length());
            
            return sql;
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("Error during SQL generation: error={}, totalTimeMs={}", e.getMessage(), totalTime, e);
            if (e instanceof IOException) {
                throw (IOException) e;
            } else if (e instanceof InterruptedException) {
                throw (InterruptedException) e;
            } else {
                throw new RuntimeException("Error generating SQL", e);
            }
        }
    }

    private String buildSystemPrompt(String schemaContext) {
        return "You are a BigQuery SQL expert specializing in the NCAA basketball dataset.\n\n"
               + "Dataset: " + datasetName + "." + schemaName + "\n\n"
               + "Schema Information:\n" + schemaContext + "\n\n"
               + "CRITICAL COLUMN COUNT RULES (MUST FOLLOW):\n"
               + "- For COMPARISON queries (top/bottom/ranking): Return EXACTLY 2 columns (label + 1 metric)\n"
               + "- For TREND queries (over time/seasons): Return 2-3 columns (temporal + label + metric OR temporal + metric)\n"
               + "- For DISTRIBUTION queries (breakdown/percentage): Return EXACTLY 2 columns (category + 1 positive metric)\n"
               + "- For MULTI-METRIC queries: Maximum 4 columns (1 label + 3 metrics)\n"
               + "- For TABLE display (explicit 'show tabular'): Maximum 8 columns\n\n"
               + "SQL GENERATION RULES:\n"
               + "1. MINIMIZE columns - only include what's needed for visualization\n"
               + "2. Use descriptive column aliases (e.g., 'player_name', 'avg_points', 'total_rebounds')\n"
               + "3. Use proper aggregations: AVG() for averages, SUM() for totals, COUNT() for counts\n"
               + "4. Include GROUP BY for aggregations\n"
               + "5. Use ORDER BY to sort meaningfully (DESC for top/most, ASC for chronological)\n"
               + "6. Always LIMIT results (10 for top/bottom, 100 for lists, 5-8 for pie charts)\n"
               + "7. Use table: `bigquery-public-data.ncaa_basketball.table_name`\n"
               + "8. Return ONLY executable SQL - no markdown, no explanations\n\n"
               + "BAD EXAMPLE (too many columns):\n"
               + "SELECT full_name, team, season, points, rebounds, assists, steals, blocks, field_goal_pct\n\n"
               + "GOOD EXAMPLE (comparison/ranking):\n"
               + "SELECT full_name, AVG(points) as avg_points\n"
               + "FROM `bigquery-public-data.ncaa_basketball.mbb_players_games_sr`\n"
               + "WHERE season = 2016\n"
               + "GROUP BY full_name\n"
               + "ORDER BY avg_points DESC\n"
               + "LIMIT 10\n";
    }

    private String extractSqlFromResponse(GenerateContentResponse response) {
        // Extract text from the response
        String text = response.text();
        
        // Remove markdown code blocks if present (```sql or ``` wrappers)
        text = text.replaceAll("```sql", "").replaceAll("```", "").trim();
        
        // NOTE: Do NOT extract content between single backticks, as BigQuery SQL
        // uses backticks for table identifiers (e.g., `dataset.table`)
        // Only markdown code blocks should be removed
        
        return text.trim();
    }

    private void validateSqlCompleteness(String sql) {
        String trimmedSql = sql.trim().toUpperCase();
        
        // Check if SQL starts with a valid keyword
        String[] validStarters = {"SELECT", "WITH"};
        boolean isValid = false;
        for (String starter : validStarters) {
            if (trimmedSql.startsWith(starter)) {
                isValid = true;
                break;
            }
        }
        
        if (!isValid) {
            logger.error("Invalid SQL - does not start with SELECT or WITH: sqlPreview={}", 
                    sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
            throw new IllegalArgumentException("Generated SQL is incomplete. SQL must start with SELECT or WITH. Got: " + 
                    (sql.length() > 100 ? sql.substring(0, 100) + "..." : sql));
        }
        
        // Check if SQL contains FROM clause (for SELECT queries)
        if (trimmedSql.startsWith("SELECT") && !trimmedSql.contains("FROM")) {
            logger.error("Invalid SQL - SELECT without FROM: sqlPreview={}", 
                    sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
            throw new IllegalArgumentException("Generated SQL is incomplete. SELECT query must contain FROM clause.");
        }
        
        logger.debug("SQL completeness validation passed");
    }
    
    private void validateSqlSafety(String sql) {
        String upperSql = sql.toUpperCase();
        String[] dangerousKeywords = {"DROP", "DELETE", "UPDATE", "INSERT", "ALTER", "TRUNCATE", "CREATE", "GRANT", "REVOKE"};
        
        for (String keyword : dangerousKeywords) {
            if (upperSql.contains(keyword)) {
                logger.warn("Unsafe SQL detected: keyword={}, sqlPreview={}", 
                        keyword, sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
                throw new IllegalArgumentException("Unsafe SQL detected: " + keyword + " operations are not allowed");
            }
        }
        logger.debug("SQL safety validation passed");
    }
}

