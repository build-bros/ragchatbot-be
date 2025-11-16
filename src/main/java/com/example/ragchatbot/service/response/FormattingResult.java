package com.example.ragchatbot.service.response;

import com.example.ragchatbot.service.analysis.ResultStatsSummary;
import com.example.ragchatbot.service.visualization.QueryIntent;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Wrapper for formatted chat responses that also tracks diagnostics required for
 * logging and analytics.
 */
public class FormattingResult {
    private final Map<String, Object> responseBody;
    private final QueryIntent queryIntent;
    private final ResultStatsSummary resultStats;
    private final String selectedChartType;
    private final TemplateResult templateResult;
    private final Optional<QueryLogRecommendation> recommendation;

    public FormattingResult(Map<String, Object> responseBody,
                            QueryIntent queryIntent,
                            ResultStatsSummary resultStats,
                            String selectedChartType,
                            TemplateResult templateResult,
                            Optional<QueryLogRecommendation> recommendation) {
        this.responseBody = responseBody != null ? responseBody : Collections.emptyMap();
        this.queryIntent = queryIntent;
        this.resultStats = resultStats;
        this.selectedChartType = selectedChartType;
        this.templateResult = templateResult;
        this.recommendation = recommendation;
    }

    public Map<String, Object> getResponseBody() {
        return responseBody;
    }

    public QueryIntent getQueryIntent() {
        return queryIntent;
    }

    public ResultStatsSummary getResultStats() {
        return resultStats;
    }

    public String getSelectedChartType() {
        return selectedChartType;
    }

    public TemplateResult getTemplateResult() {
        return templateResult;
    }

    public Optional<QueryLogRecommendation> getRecommendation() {
        return recommendation;
    }
}

