package com.healthlog.dto;

import com.healthlog.entity.HealthLog;
import java.time.LocalDate;

/**
 * 對外輸出的健康日誌 DTO（Controller 不直接吐 Entity，§10.4）。
 */
public class HealthLogDto {
    private Long id;
    private LocalDate logDate;
    private Double sleepHours;
    private Integer steps;
    private Integer moodScore;
    private String riskLevel;

    public static HealthLogDto from(HealthLog e) {
        HealthLogDto d = new HealthLogDto();
        d.id = e.getId();
        d.logDate = e.getLogDate();
        d.sleepHours = e.getSleepHours();
        d.steps = e.getSteps();
        d.moodScore = e.getMoodScore();
        d.riskLevel = e.getRiskLevel();
        return d;
    }

    public Long getId() { return id; }
    public LocalDate getLogDate() { return logDate; }
    public Double getSleepHours() { return sleepHours; }
    public Integer getSteps() { return steps; }
    public Integer getMoodScore() { return moodScore; }
    public String getRiskLevel() { return riskLevel; }
}
