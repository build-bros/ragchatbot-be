package com.example.ragchatbot.service.response;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents guidance sourced from historical query logs (chart type, template, etc.).
 */
public class QueryLogRecommendation {
    private final String chartType;
    private final String templateId;
    private final double support;

    public QueryLogRecommendation(String chartType, String templateId, double support) {
        this.chartType = chartType;
        this.templateId = templateId;
        this.support = support;
    }

    public String getChartType() {
        return chartType;
    }

    public String getTemplateId() {
        return templateId;
    }

    public double getSupport() {
        return support;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("chartType", chartType);
        map.put("templateId", templateId);
        map.put("support", support);
        return map;
    }
}

