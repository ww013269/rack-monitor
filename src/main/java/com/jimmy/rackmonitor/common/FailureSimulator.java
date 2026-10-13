package com.jimmy.rackmonitor.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Demo 用：設定 DEMO_UNHEALTHY=true 時，應用程式啟動即失敗（模擬「壞掉的新版本」）。
 * 新 Pod 永遠不會 ready → rollout status 逾時 → pipeline 自動回滾；
 * 因為 maxUnavailable: 0，過程中舊版 Pod 持續服務。
 */
@Component
public class FailureSimulator {

    public FailureSimulator(@Value("${demo.unhealthy:false}") boolean unhealthy) {
        if (unhealthy) {
            throw new IllegalStateException("demo.unhealthy=true：模擬壞掉的版本，啟動失敗");
        }
    }
}
