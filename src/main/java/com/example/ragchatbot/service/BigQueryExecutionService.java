package com.example.ragchatbot.service;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.Job;
import com.google.cloud.bigquery.JobId;
import com.google.cloud.bigquery.JobInfo;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BigQueryExecutionService {

    private static final Logger logger = LoggerFactory.getLogger(BigQueryExecutionService.class);

    private final BigQuery bigQuery;

    public BigQueryExecutionService() {
        logger.info("Initializing BigQuery service");
        this.bigQuery = BigQueryOptions.getDefaultInstance().getService();
        logger.info("BigQuery service initialized successfully");
    }

    public List<List<Object>> executeQuery(String sql) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        String jobId = UUID.randomUUID().toString();
        logger.info("Executing BigQuery query: jobId={}, sqlLength={}, sqlPreview={}", 
                jobId, sql.length(), sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
        
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
                    .setUseLegacySql(false)
                    .build();

            JobId bigQueryJobId = JobId.of(jobId);
            long jobCreateStart = System.currentTimeMillis();
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(bigQueryJobId).build());
            long jobCreateTime = System.currentTimeMillis() - jobCreateStart;
            logger.debug("BigQuery job created: jobId={}, jobCreateTimeMs={}", jobId, jobCreateTime);

            // Wait for the query to complete
            long waitStart = System.currentTimeMillis();
            queryJob = queryJob.waitFor();
            long waitTime = System.currentTimeMillis() - waitStart;
            logger.debug("BigQuery job completed: jobId={}, waitTimeMs={}", jobId, waitTime);

            if (queryJob == null) {
                logger.error("BigQuery job no longer exists: jobId={}", jobId);
                throw new RuntimeException("Job no longer exists");
            }
            
            var error = queryJob.getStatus().getError();
            if (error != null) {
                logger.error("BigQuery query failed: jobId={}, error={}", jobId, error.toString());
                throw new RuntimeException("Query failed: " + error.toString());
            }

            long resultStart = System.currentTimeMillis();
            TableResult result = queryJob.getQueryResults();
            
            List<List<Object>> rows = new ArrayList<>();
            result.iterateAll().forEach(row -> {
                List<Object> rowData = new ArrayList<>();
                row.forEach(fieldValue -> rowData.add(fieldValue.getValue()));
                rows.add(rowData);
            });
            long resultTime = System.currentTimeMillis() - resultStart;
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("BigQuery query executed successfully: jobId={}, totalTimeMs={}, waitTimeMs={}, resultTimeMs={}, rowCount={}", 
                    jobId, totalTime, waitTime, resultTime, rows.size());

            return rows;
        } catch (InterruptedException e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("BigQuery query interrupted: jobId={}, totalTimeMs={}", jobId, totalTime, e);
            throw e;
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("BigQuery query execution failed: jobId={}, error={}, totalTimeMs={}", 
                    jobId, e.getMessage(), totalTime, e);
            throw e;
        }
    }

    public List<String> getColumnNames(String sql) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        String jobId = UUID.randomUUID().toString();
        logger.debug("Getting column names: jobId={}, sqlLength={}", jobId, sql.length());
        
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
                    .setUseLegacySql(false)
                    .build();

            JobId bigQueryJobId = JobId.of(jobId);
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(bigQueryJobId).build());
            logger.debug("BigQuery job created for column names: jobId={}", jobId);

            queryJob = queryJob.waitFor();

            if (queryJob == null) {
                logger.error("BigQuery job no longer exists: jobId={}", jobId);
                throw new RuntimeException("Job no longer exists");
            }
            
            var error = queryJob.getStatus().getError();
            if (error != null) {
                logger.error("BigQuery query failed while getting column names: jobId={}, error={}", 
                        jobId, error.toString());
                throw new RuntimeException("Query failed: " + error.toString());
            }

            TableResult result = queryJob.getQueryResults();
            List<String> columnNames = new ArrayList<>();
            
            var schema = result.getSchema();
            if (schema != null && schema.getFields() != null) {
                schema.getFields().forEach(field -> columnNames.add(field.getName()));
            }

            long totalTime = System.currentTimeMillis() - startTime;
            logger.debug("Column names retrieved: jobId={}, columnCount={}, totalTimeMs={}", 
                    jobId, columnNames.size(), totalTime);

            return columnNames;
        } catch (InterruptedException e) {
            logger.error("BigQuery query interrupted while getting column names: jobId={}", jobId, e);
            throw e;
        } catch (Exception e) {
            logger.error("Failed to get column names: jobId={}, error={}", jobId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Executes a BigQuery SQL query and returns a rich result wrapper with metadata.
     * This method provides access to column types, efficient data access, and other metadata.
     * 
     * @param sql The SQL query to execute
     * @return BigQueryResult wrapper containing the TableResult and metadata
     * @throws InterruptedException if the query execution is interrupted
     */
    public BigQueryResult executeQueryRich(String sql) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        String jobId = UUID.randomUUID().toString();
        logger.info("Executing BigQuery query (rich): jobId={}, sqlLength={}, sqlPreview={}", 
                jobId, sql.length(), sql.length() > 200 ? sql.substring(0, 200) + "..." : sql);
        
        try {
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(sql)
                    .setUseLegacySql(false)
                    .build();

            JobId bigQueryJobId = JobId.of(jobId);
            long jobCreateStart = System.currentTimeMillis();
            Job queryJob = bigQuery.create(JobInfo.newBuilder(queryConfig).setJobId(bigQueryJobId).build());
            long jobCreateTime = System.currentTimeMillis() - jobCreateStart;
            logger.debug("BigQuery job created: jobId={}, jobCreateTimeMs={}", jobId, jobCreateTime);

            // Wait for the query to complete
            long waitStart = System.currentTimeMillis();
            queryJob = queryJob.waitFor();
            long waitTime = System.currentTimeMillis() - waitStart;
            logger.debug("BigQuery job completed: jobId={}, waitTimeMs={}", jobId, waitTime);

            if (queryJob == null) {
                logger.error("BigQuery job no longer exists: jobId={}", jobId);
                throw new RuntimeException("Job no longer exists");
            }
            
            var error = queryJob.getStatus().getError();
            if (error != null) {
                logger.error("BigQuery query failed: jobId={}, error={}", jobId, error.toString());
                throw new RuntimeException("Query failed: " + error.toString());
            }

            long resultStart = System.currentTimeMillis();
            TableResult tableResult = queryJob.getQueryResults();
            BigQueryResult result = new BigQueryResult(tableResult);
            long resultTime = System.currentTimeMillis() - resultStart;
            
            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("BigQuery query executed successfully (rich): jobId={}, totalTimeMs={}, waitTimeMs={}, resultTimeMs={}, rowCount={}, columnCount={}", 
                    jobId, totalTime, waitTime, resultTime, result.getRowCount(), result.getColumnCount());

            return result;
        } catch (InterruptedException e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("BigQuery query interrupted: jobId={}, totalTimeMs={}", jobId, totalTime, e);
            throw e;
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            logger.error("BigQuery query execution failed: jobId={}, error={}, totalTimeMs={}", 
                    jobId, e.getMessage(), totalTime, e);
            throw e;
        }
    }
}

