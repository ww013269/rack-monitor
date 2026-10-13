package com.jimmy.rackmonitor.reading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 純單元測試：不啟動 Spring，只驗告警判定邏輯。 */
class ReadingServiceTest {

    private final ReadingService service = new ReadingService(27.0);

    @Test
    void 溫度未超過門檻為NORMAL() {
        SensorReading reading = service.record(new ReadingRequest("R-01", 4.2, 24.5));
        assertThat(reading.status()).isEqualTo(SensorReading.Status.NORMAL);
    }

    @Test
    void 溫度超過門檻為ALERT() {
        SensorReading reading = service.record(new ReadingRequest("R-01", 4.2, 29.0));
        assertThat(reading.status()).isEqualTo(SensorReading.Status.ALERT);
    }

    @Test
    void 只保留每個機櫃最新一筆() {
        service.record(new ReadingRequest("R-02", 3.0, 22.0));
        service.record(new ReadingRequest("R-02", 3.5, 23.0));
        assertThat(service.latest("R-02")).get()
                .extracting(SensorReading::inletTemp)
                .isEqualTo(23.0);
    }
}
