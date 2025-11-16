package com.example.ragchatbot.service.response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the structured template applied to a response message.
 */
public class TemplateResult {
    private final String templateId;
    private final String chartType;
    private final String headline;
    private final String explanation;
    private final List<String> insights;
    private final String intentLabel;

    public TemplateResult(String templateId,
                          String chartType,
                          String headline,
                          String explanation,
                          List<String> insights,
                          String intentLabel) {
        this.templateId = templateId;
        this.chartType = chartType;
        this.headline = headline;
        this.explanation = explanation;
        this.insights = insights != null ? new ArrayList<>(insights) : new ArrayList<>();
        this.intentLabel = intentLabel;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getChartType() {
        return chartType;
    }

    public String getHeadline() {
        return headline;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<String> getInsights() {
        return new ArrayList<>(insights);
    }

    public String getIntentLabel() {
        return intentLabel;
    }

    public String composeMessage() {
        StringBuilder builder = new StringBuilder(headline);
        if (explanation != null && !explanation.isBlank()) {
            builder.append("\n\n").append(explanation);
        }
        if (!insights.isEmpty()) {
            builder.append("\n\nKey takeaways:\n");
            for (String insight : insights) {
                builder.append("• ").append(insight).append("\n");
            }
        }
        return builder.toString().trim();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("templateId", templateId);
        map.put("chartType", chartType);
        map.put("headline", headline);
        map.put("explanation", explanation);
        map.put("insights", insights);
        map.put("intentLabel", intentLabel);
        return map;
    }
}

