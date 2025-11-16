package com.example.ragchatbot.service.analysis;

import com.example.ragchatbot.service.data.BigQueryResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResultStatsCollectorTest {

    @Test
    void choosesNonTemporalMetricAndTemporalDimensionForSeasonTrend() {
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

        ResultStatsCollector collector = new ResultStatsCollector();
        ResultStatsSummary summary = collector.summarize(result);

        assertThat(summary.getPrimaryMetric()).isEqualTo("avg_3_pointers_made_per_game");
        assertThat(summary.getPrimaryDimension()).isEqualTo("season");
        assertThat(summary.hasTemporalDimension()).isTrue();
    }
}


