package com.example.ragchatbot.service.data.transformer;

import com.example.ragchatbot.service.data.BigQueryResult;
import com.example.ragchatbot.service.visualization.QueryIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for selecting and creating appropriate result transformers
 * based on BigQuery result characteristics and query intent.
 */
@Component
public class TransformerFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(TransformerFactory.class);
    
    private final List<ResultTransformer> transformers;
    
    @Autowired
    public TransformerFactory(List<ResultTransformer> transformers) {
        this.transformers = transformers != null ? new ArrayList<>(transformers) : new ArrayList<>();
        
        // Sort transformers by priority (highest first)
        this.transformers.sort((t1, t2) -> Integer.compare(t2.getPriority(), t1.getPriority()));
        
        logger.info("TransformerFactory initialized with {} transformers", this.transformers.size());
    }
    
    /**
     * Gets the appropriate transformer for the given result and intent.
     * Returns the first transformer that can handle the result.
     * 
     * @param result The BigQuery result to transform
     * @param intent The query intent with visualization preferences
     * @return The selected transformer, never null (falls back to TableTransformer)
     */
    public ResultTransformer getTransformer(BigQueryResult result, QueryIntent intent) {
        return getTransformer(result, intent, null);
    }

    /**
     * Gets a transformer but prioritizes the provided chart type if possible.
     */
    public ResultTransformer getTransformer(BigQueryResult result,
                                            QueryIntent intent,
                                            String preferredChartType) {
        if (preferredChartType != null) {
            for (ResultTransformer transformer : transformers) {
                if (transformer.getTargetChartType().equalsIgnoreCase(preferredChartType)
                        && transformer.canTransform(result, intent)) {
                    logger.debug("Selected preferred transformer: chartType={}, priority={}",
                            transformer.getTargetChartType(), transformer.getPriority());
                    return transformer;
                }
            }
        }

        for (ResultTransformer transformer : transformers) {
            if (transformer.canTransform(result, intent)) {
                logger.debug("Selected transformer: chartType={}, priority={}",
                        transformer.getTargetChartType(), transformer.getPriority());
                return transformer;
            }
        }

        ResultTransformer tableTransformer = transformers.stream()
                .filter(t -> t.getTargetChartType().equals("table"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Table transformer not found"));

        logger.debug("Using fallback table transformer");
        return tableTransformer;
    }
    
    /**
     * Gets all available transformers.
     * 
     * @return List of all transformers, sorted by priority
     */
    public List<ResultTransformer> getAllTransformers() {
        return new ArrayList<>(transformers);
    }
}

