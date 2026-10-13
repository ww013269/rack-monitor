package com.jimmy.rackmonitor.reading;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 保存每個機櫃的最新一筆資料（記憶體版，示範用）。
 * 告警門檻由設定檔 / 環境變數 RACK_THRESHOLD 決定，部署時由 K8s ConfigMap 注入。
 */
@Service
public class ReadingService {

    private final double threshold;
    private final Map<String, SensorReading> latestByRack = new ConcurrentHashMap<>();

    public ReadingService(@Value("${rack.threshold:27.0}") double threshold) {
        this.threshold = threshold;
    }

    public SensorReading record(ReadingRequest request) {
        SensorReading.Status status = request.inletTemp() > threshold
                ? SensorReading.Status.ALERT
                : SensorReading.Status.NORMAL;

        SensorReading reading = new SensorReading(
                request.rackId(),
                request.powerKw(),
                request.inletTemp(),
                status,
                Instant.now());

        latestByRack.put(reading.rackId(), reading);
        return reading;
    }

    public Optional<SensorReading> latest(String rackId) {
        return Optional.ofNullable(latestByRack.get(rackId));
    }

    public double threshold() {
        return threshold;
    }
}
