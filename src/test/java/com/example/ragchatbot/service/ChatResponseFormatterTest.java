package com.example.ragchatbot.service;

import com.example.ragchatbot.service.analysis.ResultStatsCollector;
import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.transformer.*;
import com.example.ragchatbot.service.response.FormattingResult;
import com.example.ragchatbot.service.response.QueryLogInsightsService;
import com.example.ragchatbot.service.response.ResponseTemplateEngine;
import com.example.ragchatbot.service.visualization.BarChartStrategy;
import com.example.ragchatbot.service.visualization.BubbleChartStrategy;
import com.example.ragchatbot.service.visualization.LineChartStrategy;
import com.example.ragchatbot.service.visualization.MultiLineChartStrategy;
import com.example.ragchatbot.service.visualization.QueryAnalyzer;
import com.example.ragchatbot.service.visualization.QueryIntent;
import com.example.ragchatbot.service.visualization.TableStrategy;
import com.example.ragchatbot.service.visualization.VisualizationStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatResponseFormatterTest {

    @Mock
    private QueryAnalyzer queryAnalyzer;

    @Mock
    private QueryLogInsightsService queryLogInsightsService;

    private ChatResponseFormatter formatter;

    @BeforeEach
    void setUp() {
        TransformerFactory transformerFactory = new TransformerFactory(List.of(
                new BubbleChartTransformer(),
                new BarChartTransformer(),
                new LineChartTransformer(),
                new PieChartTransformer(),
                new TableTransformer()
        ));

        List<VisualizationStrategy> strategies = List.of(
                new MultiLineChartStrategy(),
                new LineChartStrategy(),
                new BarChartStrategy(),
                new BubbleChartStrategy(),
                new TableStrategy()
        );

        ResultStatsCollector statsCollector = new ResultStatsCollector();
        ResponseTemplateEngine templateEngine = new ResponseTemplateEngine();

        formatter = new ChatResponseFormatter(
                queryAnalyzer,
                transformerFactory,
                strategies,
                statsCollector,
                templateEngine,
                queryLogInsightsService
        );
        when(queryLogInsightsService.findRecommendation(any(), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void honorsExplicitBubbleChartWhenDataSupportsIt() {
        Map<String, Double> scores = new HashMap<>();
        scores.put("bar", 230.0);
        scores.put("bubble", 115.0);
        scores.put("pie", 95.0);
        scores.put("line", 0.0);
        scores.put("table", 0.1);

        QueryIntent explicitBubbleIntent = new QueryIntent(
                scores,
                "comparison",
                true,
                "bubble",
                null,
                1.0
        );

        when(queryAnalyzer.analyze(anyString(), any(), any()))
                .thenReturn(explicitBubbleIntent);

        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("team_name", "average_points", "average_rebounds", "average_assists"),
                List.of("STRING", "FLOAT64", "FLOAT64", "FLOAT64"),
                List.of(
                        List.of("Rockets", 89.0, 23.5, 14.0),
                        List.of("Demons", 86.25, 34.75, 17.0),
                        List.of("Cyclones", 83.0, 35.0, 16.0)
                )
        );

        FormattingResult formattingResult = formatter.formatResponse(
                "Show a bubble chart comparing teams’ average points, rebounds, and assists per game in the 2013 season (top 10 teams).",
                "SELECT ...",
                result
        );

        assertThat(formattingResult.getSelectedChartType()).isEqualTo("bubble");
        Object graphData = formattingResult.getResponseBody().get("graphData");
        assertThat(graphData).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> graphMap = (Map<String, Object>) graphData;
        assertThat(graphMap.get("chartType")).isEqualTo("bubble");
        assertThat(graphMap.get("sizes")).asList().hasSize(3);
    }

    @Test
    void fallsBackWhenBubbleNotSupportedByData() {
        Map<String, Double> scores = new HashMap<>();
        scores.put("bar", 230.0);
        scores.put("bubble", 115.0);
        scores.put("pie", 95.0);
        scores.put("line", 0.0);
        scores.put("table", 0.1);

        QueryIntent explicitBubbleIntent = new QueryIntent(
                scores,
                "comparison",
                true,
                "bubble",
                null,
                1.0
        );

        when(queryAnalyzer.analyze(anyString(), any(), any()))
                .thenReturn(explicitBubbleIntent);

        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("team_name", "conference", "average_points"),
                List.of("STRING", "STRING", "FLOAT64"),
                List.of(
                        List.of("Rockets", "South", 89.0),
                        List.of("Demons", "West", 86.25),
                        List.of("Cyclones", "North", 83.0)
                )
        );

        FormattingResult formattingResult = formatter.formatResponse(
                "Show a bubble chart comparing teams’ average points in the 2013 season (top 10 teams).",
                "SELECT ...",
                result
        );

        assertThat(formattingResult.getSelectedChartType()).isEqualTo("bar");
        Object graphData = formattingResult.getResponseBody().get("graphData");
        assertThat(graphData).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> graphMap = (Map<String, Object>) graphData;
        assertThat(graphMap.get("chartType")).isEqualTo("bar");
        assertThat(graphMap.get("y")).asList().hasSize(3);
    }

    @Test
    void rendersMultiSeriesLineChartAsMultiLineVisualization() {
        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("season", "team_name", "avg_points_per_game"),
                List.of("INT64", "STRING", "FLOAT64"),
                List.of(
                        List.of(2013, "Generals", 64.0),
                        List.of(2013, "Golden Grizzlies", 74.39),
                        List.of(2014, "Generals", 71.6),
                        List.of(2014, "Golden Grizzlies", 74.24),
                        List.of(2015, "Generals", 85.5),
                        List.of(2015, "Golden Grizzlies", 86.37)
                )
        );

        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 250.0);
        scores.put("bar", 110.0);
        scores.put("bubble", 0.0);
        scores.put("pie", 0.0);
        scores.put("table", 0.1);

        QueryIntent lineIntent = new QueryIntent(
                scores,
                "trend",
                true,
                "line",
                null,
                1.0
        );

        when(queryAnalyzer.analyze(anyString(), any(), any()))
                .thenReturn(lineIntent);

        FormattingResult formattingResult = formatter.formatResponse(
                "Show a line chart comparing average points per game by season for the top scoring teams.",
                "SELECT ...",
                result
        );

        assertThat(formattingResult.getSelectedChartType()).isEqualTo("multi_line");
        Object graphData = formattingResult.getResponseBody().get("graphData");
        assertThat(graphData).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> graphMap = (Map<String, Object>) graphData;
        assertThat(graphMap.get("chartType")).isEqualTo("multi_line");
        assertThat(graphMap.get("series")).asList().hasSize(2);
        assertThat(graphMap).doesNotContainKey("columns");
    }
}

