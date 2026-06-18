package com.healthlog.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 身體數據輸入 DTO（含驗證） */
public class BodyMetricRequest {
    private LocalDate recordDate;

    @DecimalMin(value = "30.0", message = "身高需 ≥ 30 公分")
    @DecimalMax(value = "250.0", message = "身高需 ≤ 250 公分")
    private Double heightCm;

    @DecimalMin(value = "2.0", message = "體重需 ≥ 2 公斤")
    @DecimalMax(value = "400.0", message = "體重需 ≤ 400 公斤")
    private Double weightKg;

    @Min(value = 0, message = "喝水量不可為負")
    @Max(value = 20000, message = "喝水量數值異常")
    private Integer waterMl;

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public Integer getWaterMl() { return waterMl; }
    public void setWaterMl(Integer waterMl) { this.waterMl = waterMl; }
}
