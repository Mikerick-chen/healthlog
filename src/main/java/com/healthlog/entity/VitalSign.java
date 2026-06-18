package com.healthlog.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 生命徵象 Entity — 新增資料表 vital_signs。
 * 記錄血壓（收縮/舒張）、心率、體溫、血糖（依使用者需求新增血壓與血糖）。
 */
@Entity
@Table(name = "vital_signs")
public class VitalSign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 收縮壓 mmHg（高壓） */
    @Column(name = "systolic")
    private Integer systolic;

    /** 舒張壓 mmHg（低壓） */
    @Column(name = "diastolic")
    private Integer diastolic;

    /** 心率 bpm */
    @Column(name = "heart_rate")
    private Integer heartRate;

    /** 體溫 ℃ */
    @Column(name = "body_temp")
    private Double bodyTemp;

    /** 血糖 mg/dL */
    @Column(name = "blood_sugar")
    private Integer bloodSugar;

    /** 量測情境：空腹 / 飯後 / 隨機（供血糖判讀參考） */
    @Column(name = "measure_context")
    private String measureContext;

    public VitalSign() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
