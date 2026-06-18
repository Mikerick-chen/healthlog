package com.healthlog.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/** 生命徵象輸入 DTO（含驗證） */
public class VitalSignRequest {
    private LocalDate recordDate;

    @Min(value = 50, message = "收縮壓數值異常")
    @Max(value = 300, message = "收縮壓數值異常")
    private Integer systolic;

    @Min(value = 30, message = "舒張壓數值異常")
    @Max(value = 200, message = "舒張壓數值異常")
    private Integer diastolic;

    @Min(value = 20, message = "心率數值異常")
    @Max(value = 250, message = "心率數值異常")
    private Integer heartRate;

    @DecimalMin(value = "30.0", message = "體溫數值異常")
    @DecimalMax(value = "45.0", message = "體溫數值異常")
    private Double bodyTemp;

    @Min(value = 20, message = "血糖數值異常")
    @Max(value = 800, message = "血糖數值異常")
    private Integer bloodSugar;

    /** 空腹 / 飯後 / 隨機 */
    private String measureContext;

    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public Integer getSystolic() { return systolic; }
    public void setSystolic(Integer systolic) { this.systolic = systolic; }
    public Integer getDiastolic() { return diastolic; }
    public void setDiastolic(Integer diastolic) { this.diastolic = diastolic; }
    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }
    public Double getBodyTemp() { return bodyTemp; }
    public void setBodyTemp(Double bodyTemp) { this.bodyTemp = bodyTemp; }
    public Integer getBloodSugar() { return bloodSugar; }
    public void setBloodSugar(Integer bloodSugar) { this.bloodSugar = bloodSugar; }
    public String getMeasureContext() { return measureContext; }
    public void setMeasureContext(String measureContext) { this.measureContext = measureContext; }
}
