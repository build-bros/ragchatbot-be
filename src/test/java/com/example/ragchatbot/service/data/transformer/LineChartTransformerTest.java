package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.data.TransformedData;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LineChartTransformerTest {

    @Test
    void usesSeasonAsXAxisAndMetricAsYAxis() {
        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("season", "avg_3_pointers_made_per_game"),
                List.of("INT64", "FLOAT64"),
                List.of(
                        List.of(2013, 12.35),
                        List.of(2014, 12.39),
                        List.of(2015, 13.78),
                        List.of(2016, 14.80),
                        List.of(2017, 15.34)
                )
        );

        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 230.0);
        scores.put("bar", 110.0);
        scores.put("pie", 0.0);
        scores.put("bubble", 0.0);
        scores.put("table", 0.1);

        QueryIntent intent = new QueryIntent(scores, "trend", true, "line", null, 1.0);

        LineChartTransformer transformer = new LineChartTransformer();
        assertThat(transformer.canTransform(result, intent)).isTrue();

        TransformedData transformed = transformer.transform(result);

        assertThat(transformed.getChartType()).isEqualTo("line");
        assertThat(transformed.get("x")).asList().containsExactly(2013, 2014, 2015, 2016, 2017);
        assertThat(transformed.get("y")).asList().containsExactly(12.35, 12.39, 13.78, 14.80, 15.34);
        assertThat(transformed.get("xLabel")).isEqualTo("season");
        assertThat(transformed.get("yLabel")).isEqualTo("avg_3_pointers_made_per_game");
    }

    @Test
    void buildsMultipleSeriesWhenCategoryColumnExists() {
        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("season", "team_name", "avg_points"),
                List.of("INT64", "STRING", "FLOAT64"),
                List.of(
                        List.of(2015, "Tar Heels", 84.5),
                        List.of(2015, "Bluejays", 83.2),
                        List.of(2016, "Tar Heels", 86.1),
                        List.of(2016, "Bluejays", 84.7)
                )
        );

        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 230.0);
        scores.put("bar", 110.0);
        scores.put("pie", 0.0);
        scores.put("bubble", 0.0);
        scores.put("table", 0.1);

        QueryIntent intent = new QueryIntent(scores, "trend", true, "line", null, 1.0);
        LineChartTransformer transformer = new LineChartTransformer();
        TransformedData transformed = transformer.transform(result);

        assertThat(transformed.getChartType()).isEqualTo("multi_line");
        assertThat(transformed.containsKey("series")).isTrue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) transformed.get("series");
        assertThat(series).hasSize(2);
        assertThat(series.get(0).get("name")).isEqualTo("Tar Heels");
        assertThat(series.get(1).get("name")).isEqualTo("Bluejays");
    }

    @Test
    void usesMultiLineChartTypeWhenMultipleTeamsAcrossSeasons() {
        BigQueryResult result = BigQueryResult.fromCachedData(
                List.of("season", "team_name", "avg_points_per_game"),
                List.of("INT64", "STRING", "FLOAT64"),
                List.of(
                        List.of(2013, "Generals", 64.0),
                        List.of(2013, "Golden Grizzlies", 74.39),
                        List.of(2014, "Generals", 71.6),
                        List.of(2014, "Golden Grizzlies", 74.24)
                )
        );

        Map<String, Double> scores = new HashMap<>();
        scores.put("line", 230.0);
        scores.put("bar", 110.0);
        scores.put("pie", 0.0);
        scores.put("bubble", 0.0);
        scores.put("table", 0.1);

        QueryIntent intent = new QueryIntent(scores, "trend", true, "line", null, 1.0);
        LineChartTransformer transformer = new LineChartTransformer();

        TransformedData transformed = transformer.transform(result);

        assertThat(transformed.getChartType()).isEqualTo("multi_line");
        assertThat(transformed.containsKey("series")).isTrue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> series = (List<Map<String, Object>>) transformed.get("series");
        assertThat(series).hasSize(2);
        assertThat(series.get(0).get("x")).asList().contains(2013, 2014);
    }
}


