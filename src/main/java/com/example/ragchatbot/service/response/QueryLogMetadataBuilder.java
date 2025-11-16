package com.example.ragchatbot.service.response;

import com.example.ragchatbot.service.analysis.ResultStatsSummary;
import com.example.ragchatbot.service.visualization.QueryIntent;
import com.example.ragchatbot.util.SqlSignatureUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility for translating runtime formatting diagnostics into a persistable
 * metadata structure stored alongside cached queries.
 */
public final class QueryLogMetadataBuilder {

    private QueryLogMetadataBuilder() {
    }

    public static Map<String, Object> build(String sql, FormattingResult result) {
        Map<String, Object> metadata = new HashMap<>();
        String normalizedSql = SqlSignatureUtil.normalize(sql);
        if (normalizedSql != null) {
            metadata.put("normalizedSql", normalizedSql);
        }

        metadata.put("selectedChartType", result.getSelectedChartType());
        metadata.put("intent", buildIntentMap(result.getQueryIntent()));

        ResultStatsSummary stats = result.getResultStats();
        if (stats != null) {
            metadata.put("resultStats", stats.toMap());
        }

        TemplateResult template = result.getTemplateResult();
        if (template != null) {
            metadata.put("responseTemplate", template.toMap());
        }

        Optional<QueryLogRecommendation> recommendation = result.getRecommendation();
        recommendation.ifPresent(rec -> metadata.put("retrieval", rec.toMap()));

        return metadata;
    }

    private static Map<String, Object> buildIntentMap(QueryIntent intent) {
        Map<String, Object> map = new HashMap<>();
        if (intent == null) {
            return map;
        }
        map.put("primaryIntent", intent.getPrimaryIntent());
        map.put("chartTypeScores", intent.getChartTypeScores());
        map.put("preferredChartType", intent.getPreferredChartType());
        map.put("confidence", intent.getConfidence());
        map.put("explicitRequest", intent.hasExplicitRequest());
        map.put("explicitChartType", intent.getExplicitChartType());
        if (intent.getSqlAnalysis() != null) {
            Map<String, Object> sql = new HashMap<>();
            sql.put("hasTemporalGrouping", intent.getSqlAnalysis().hasTemporalGrouping());
            sql.put("hasAggregation", intent.getSqlAnalysis().hasAggregation());
            sql.put("groupByColumnCount", intent.getSqlAnalysis().getGroupByColumnCount());
            sql.put("hasLimit", intent.getSqlAnalysis().hasLimit());
            sql.put("groupByColumns", intent.getSqlAnalysis().getGroupByColumns());
            map.put("sqlAnalysis", sql);
        }
        return map;
    }
}

