package com.n4monitor.flink;

import com.n4monitor.alarm.model.PointData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 1: parse raw Kafka JSON into a {@link PointData}.
 * <p>
 * Stateless, fully parallel. Drops malformed JSON so downstream never sees
 * a bad record. Uses flatMap (instead of map) so unparseable messages can
 * be silently skipped without emitting a sentinel value.
 */
public class ParsePointFunction extends RichFlatMapFunction<String, PointData> {

    private static final Logger log = LoggerFactory.getLogger(ParsePointFunction.class);

    private transient ObjectMapper objectMapper;

    @Override
    public void open(Configuration parameters) {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        log.info("Point parser started");
    }

    @Override
    public void flatMap(String message, Collector<PointData> out) {
        if (message == null || message.isBlank()) return;
        try {
            PointData point = objectMapper.readValue(message, PointData.class);
            if (point.getMqttPath() == null || point.getMqttPath().isBlank()) return;
            out.collect(point);
        } catch (Exception e) {
            log.warn("JSON parse failed, skipping: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
