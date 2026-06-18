package com.healthlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧健康日誌與風險評估系統 — 啟動類別。
 * 啟動指令：mvn spring-boot:run（或 java -jar target/health-log-1.0.0.jar）
 */
@SpringBootApplication
public class HealthLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthLogApplication.class, args);
    }
}
