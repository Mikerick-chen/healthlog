package com.healthlog.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

/**
 * 新增/修改健康日誌的輸入 DTO（§10.4 技術深度：Controller 不直接吃 Entity）。
 * 內含後端驗證：睡眠 0-24、心情 1-10、步數非負（§10.2）。
 */
public class HealthLogRequest {

    /** 日期；前端未填則由後端預設為今天 */
    private LocalDate logDate;

    @NotNull(message = "睡眠時數必填")
    @DecimalMin(value = "0.0", message = "睡眠時數不可小於 0")
    @DecimalMax(value = "24.0", message = "睡眠時數不可大於 24")
    private Double sleepHours;

    @NotNull(message = "步數必填")
    @Min(value = 0, message = "步數不可為負")
    private Integer steps;

    @NotNull(message = "心情分數必填")
    @Min(value = 1, message = "心情分數最低 1")
    @Max(value = 10, message = "心情分數最高 10")
    private Integer moodScore;

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double sleepHours) { this.sleepHours = sleepHours; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }
}
