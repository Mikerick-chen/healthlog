package com.healthlog.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 身體數據 Entity — 新增資料表 body_metrics（不動原 health_logs，§5 🔒 維持）。
 * 記錄身高、體重、喝水量；BMI 由身高體重即時計算（不存欄位）。
 */
@Entity
@Table(name = "body_metrics")
public class BodyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /** 身高（公分） */
    @Column(name = "height_cm")
    private Double heightCm;

    /** 體重（公斤） */
    @Column(name = "weight_kg")
    private Double weightKg;

    /** 當日喝水量（毫升） */
    @Column(name = "water_ml")
    private Integer waterMl;

    /** 資料歸屬使用者（§9） */
    @Column(name = "user_id")
    private Long userId;

    public BodyMetric() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public Double getHeightCm() { return heightCm; }
    public void setHeightCm(Double heightCm) { this.heightCm = heightCm; }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double weightKg) { this.weightKg = weightKg; }
    public Integer getWaterMl() { return waterMl; }
    public void setWaterMl(Integer waterMl) { this.waterMl = waterMl; }

    /** 計算 BMI = 體重(kg) / 身高(m)^2 */
    @Transient
    public Double getBmi() {
        if (heightCm == null || weightKg == null || heightCm <= 0) return null;
        double m = heightCm / 100.0;
        return Math.round(weightKg / (m * m) * 10.0) / 10.0;
    }
}
