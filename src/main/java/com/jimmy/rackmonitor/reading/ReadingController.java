package com.jimmy.rackmonitor.reading;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReadingController {

    private final ReadingService readingService;
    private final String apiKey;
    private final String appVersion;
    private final String appEnv;

    public ReadingController(
            ReadingService readingService,
            @Value("${api.key:}") String apiKey,
            @Value("${app.version:local}") String appVersion,
            @Value("${app.env:local}") String appEnv) {
        this.readingService = readingService;
        this.apiKey = apiKey;
        this.appVersion = appVersion;
        this.appEnv = appEnv;
    }

    /** 寫入一筆感測資料。需帶 X-API-KEY（金鑰由 K8s Secret 注入）。 */
    @PostMapping("/readings")
    public ResponseEntity<?> create(
            @RequestHeader(value = "X-API-KEY", required = false) String requestKey,
            @Valid @RequestBody ReadingRequest request) {

        if (!apiKey.isEmpty() && !apiKey.equals(requestKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid api key"));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(readingService.record(request));
    }

    /** 查某機櫃最新一筆資料。 */
    @GetMapping("/racks/{rackId}/latest")
    public ResponseEntity<SensorReading> latest(@PathVariable String rackId) {
        return readingService.latest(rackId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** 顯示目前跑的是哪一版（commit SHA）、哪個環境；用來確認部署與回滾結果。 */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "app", "rack-monitor",
                "version", appVersion,
                "env", appEnv,
                "alertThreshold", readingService.threshold());
    }
}
