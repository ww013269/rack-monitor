package com.jimmy.rackmonitor.reading;

import java.time.Instant;

/** 系統保存的一筆資料：多了判定結果與收到時間。 */
public record SensorReading(
        String rackId,
        double powerKw,
        double inletTemp,
        Status status,
        Instant receivedAt) {

    public enum Status { NORMAL, ALERT }
}
