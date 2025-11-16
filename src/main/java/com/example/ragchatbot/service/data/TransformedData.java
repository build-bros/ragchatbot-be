package com.example.ragchatbot.service.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Container for transformed data that is optimized for a specific visualization type.
 * Provides a flexible key-value structure for storing chart-specific data.
 */
public class TransformedData {
    private final Map<String, Object> data;
    private final String chartType;
    private final Map<String, String> metadata;

    public TransformedData(String chartType) {
        this.chartType = chartType;
        this.data = new HashMap<>();
        this.metadata = new HashMap<>();
    }

    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }

    public String getChartType() {
        return chartType;
    }

    public Map<String, String> getMetadata() {
        return new HashMap<>(metadata);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public void putMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.get(key);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public void putAll(Map<String, Object> map) {
        data.putAll(map);
    }
}

