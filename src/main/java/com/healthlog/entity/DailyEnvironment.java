package com.healthlog.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 每日環境資料 Entity（§3b）。
 * 由 Open-Meteo（免金鑰）抓取的天氣/氣壓/濕度/PM2.5，依使用者與日期保存，
 * 供與健康日誌做跨維度關聯分析。
 */
@Entity
@Table(name = "daily_environment")
public class DailyEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "location")
    private String location;

    @Column(name = "temperature")
    private Double temperature;   // °C

    @Column(name = "pressure")
    private Double pressure;      // hPa

    @Column(name = "humidity")
    private Double humidity;      // %

    @Column(name = "pm25")
    private Double pm25;          // µg/m³

    @Column(name = "weather_text")
    private String weatherText;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Double getPressure() { return pressure; }
    public void setPressure(Double pressure) { this.pressure = pressure; }
    public Double getHumidity() { return humidity; }
    public void setHumidity(Double humidity) { this.humidity = humidity; }
    public Double getPm25() { return pm25; }
    public void setPm25(Double pm25) { this.pm25 = pm25; }
    public String getWeatherText() { return weatherText; }
    public void setWeatherText(String weatherText) { this.weatherText = weatherText; }
}
