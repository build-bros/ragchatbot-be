package com.example.ragchatbot.service.response;

import com.example.ragchatbot.service.analysis.ResultStatsSummary;
import com.example.ragchatbot.service.analysis.ResultStatsSummary.CategoryMetricValue;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds structured natural-language responses based on query intent,
 * selected visualization, and lightweight result statistics.
 */
@Component
public class ResponseTemplateEngine {

    public TemplateResult buildTemplate(String userQuery,
                                        String chartType,
                                        QueryIntent intent,
                                        ResultStatsSummary stats) {
        return switch (chartType) {
            case "line", "multi_line" -> buildTrendTemplate(intent, stats);
            case "pie" -> buildDistributionTemplate(intent, stats);
            case "bar" -> buildComparisonTemplate(intent, stats);
            case "bubble" -> buildCorrelationTemplate(intent, stats);
            default -> buildTableTemplate(stats);
        };
    }

    private TemplateResult buildTrendTemplate(QueryIntent intent, ResultStatsSummary stats) {
        String metric = humanize(stats.getPrimaryMetric(), "metric value");
        String dimension = humanize(stats.getPrimaryDimension(), "time");
        String headline = String.format("Trend of %s across %s", metric, dimension);

        String explanation = "This line chart highlights how the metric changes over time.";
        if (stats.getPrimaryMetricMin() != null && stats.getPrimaryMetricMax() != null) {
            explanation = String.format(
                    "This line chart highlights how %s changes across %s (min %.2f, max %.2f).",
                    metric,
                    dimension,
                    stats.getPrimaryMetricMin(),
                    stats.getPrimaryMetricMax());
        }

        List<String> insights = buildLeaderInsights(stats);

        return new TemplateResult("trend_line", "line", headline, explanation, insights,
                intent != null ? intent.getPrimaryIntent() : "trend");
    }

    private TemplateResult buildComparisonTemplate(QueryIntent intent, ResultStatsSummary stats) {
        String metric = humanize(stats.getPrimaryMetric(), "metric");
        String dimension = humanize(stats.getPrimaryDimension(), "category");
        String headline = String.format("Comparing %s by %s", metric, dimension);

        String explanation = String.format(
                "Bars show how each %s ranks based on %s. %s rows total.",
                dimension,
                metric,
                stats.getRowCount());

        List<String> insights = buildLeaderInsights(stats);

        return new TemplateResult("comparison_bar", "bar", headline, explanation, insights,
                intent != null ? intent.getPrimaryIntent() : "comparison");
    }

    private TemplateResult buildDistributionTemplate(QueryIntent intent, ResultStatsSummary stats) {
        String metric = humanize(stats.getPrimaryMetric(), "value");
        String dimension = humanize(stats.getPrimaryDimension(), "category");
        String headline = String.format("Share of %s by %s", metric, dimension);

        String explanation = String.format(
                "Each slice represents the contribution of %s for every %s.",
                metric,
                dimension);

        List<String> insights = buildLeaderInsights(stats);

        return new TemplateResult("distribution_pie", "pie", headline, explanation, insights,
                intent != null ? intent.getPrimaryIntent() : "distribution");
    }

    private TemplateResult buildCorrelationTemplate(QueryIntent intent, ResultStatsSummary stats) {
        String headline = "Multi-metric comparison";
        String explanation = "Bubble size and position represent multiple metrics simultaneously.";
        List<String> insights = buildLeaderInsights(stats);
        return new TemplateResult("correlation_bubble", "bubble", headline, explanation, insights,
                intent != null ? intent.getPrimaryIntent() : "correlation");
    }

    private TemplateResult buildTableTemplate(ResultStatsSummary stats) {
        String headline = "Detailed table results";
        String explanation = String.format("Showing %d rows and %d columns.", stats.getRowCount(), stats.getColumnCount());
        return new TemplateResult("table_default", "table", headline, explanation, new ArrayList<>(), "table");
    }

    private List<String> buildLeaderInsights(ResultStatsSummary stats) {
        List<CategoryMetricValue> leaders = stats.getTopMetricRows();
        if (leaders.isEmpty()) {
            return Collections.emptyList();
        }

        return leaders.stream()
                .limit(3)
                .map(value -> {
                    if (value.getMetricValue() == null) {
                        return String.format("%s appears in the top results.", value.getCategory());
                    }
                    return String.format("%s leads with %.2f", value.getCategory(), value.getMetricValue());
                })
                .collect(Collectors.toList());
    }

    private String humanize(String rawName, String fallback) {
        if (rawName == null || rawName.isBlank()) {
            return fallback;
        }
        String cleaned = rawName.replaceAll("[_]+", " ").trim();
        String[] parts = cleaned.split("\\s+");
        return Arrays.stream(parts)
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ENGLISH) +
                        part.substring(1).toLowerCase(Locale.ENGLISH))
                .collect(Collectors.joining(" "));
    }
}

