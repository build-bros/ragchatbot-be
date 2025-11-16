package com.example.ragchatbot.service.response;

import com.example.ragchatbot.service.SqlQueryStorageService;
import com.example.ragchatbot.util.SqlSignatureUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Retrieves lightweight recommendations from the historical sql-queries.json log.
 */
@Service
public class QueryLogInsightsService {

    private static final Logger logger = LoggerFactory.getLogger(QueryLogInsightsService.class);

    private final SqlQueryStorageService storageService;

    public QueryLogInsightsService(SqlQueryStorageService storageService) {
        this.storageService = storageService;
    }

    public Optional<QueryLogRecommendation> findRecommendation(String sql, String userQuery) {
        String normalized = SqlSignatureUtil.normalize(sql);
        if (normalized == null) {
            return Optional.empty();
        }

        List<Map<String, Object>> queries = storageService.readAllQueriesSnapshot();
        if (queries.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Map<String, Object> query : queries) {
            Map<String, Object> analysis = getMap(query.get("analysis"));
            if (analysis == null) {
                continue;
            }
            String storedSignature = (String) analysis.get("normalizedSql");
            if (storedSignature != null && storedSignature.equals(normalized)) {
                matches.add(query);
            }
        }

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Long> chartCounts = new HashMap<>();
        Map<String, Long> templateCounts = new HashMap<>();

        for (Map<String, Object> match : matches) {
            Map<String, Object> analysis = getMap(match.get("analysis"));
            if (analysis == null) continue;
            String chart = (String) analysis.get("selectedChartType");
            if (chart != null) {
                chartCounts.merge(chart, 1L, (existing, increment) -> existing + increment);
            }
            Map<String, Object> template = getMap(analysis.get("responseTemplate"));
            if (template != null) {
                String templateId = (String) template.get("templateId");
                if (templateId != null) {
                    templateCounts.merge(templateId, 1L, (existing, increment) -> existing + increment);
                }
            }
        }

        String bestChart = chartCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        String bestTemplate = templateCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (bestChart == null) {
            return Optional.empty();
        }

        double support = matches.size() / (double) queries.size();
        logger.debug("Retrieved recommendation from log: chart={}, template={}, support={}",
                bestChart, bestTemplate, support);
        return Optional.of(new QueryLogRecommendation(bestChart, bestTemplate, support));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}

