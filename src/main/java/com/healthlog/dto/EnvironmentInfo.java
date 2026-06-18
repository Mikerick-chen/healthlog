package com.healthlog.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 跨維度環境關聯結果（§3b）。
 */
public class EnvironmentInfo {
    public boolean available;
    public String message;          // 不可用時的說明
    public String location;
    public LocalDate date;
    public Double temperature;      // °C
    public Double pressure;         // hPa
    public Double humidity;         // %
    public Double pm25;             // µg/m³
    public String weatherText;
    public List<Advisory> advisories = new ArrayList<>(); // 環境健康提醒
    public List<String> correlations = new ArrayList<>(); // 與使用者症狀的關聯（隱藏地雷）

    public static class Advisory {
        public String level;   // info / warn / danger
        public String message;
        public Advisory(String level, String message) { this.level = level; this.message = message; }
    }
}
