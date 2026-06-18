package com.healthlog.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * 健康日誌 Entity — 對應 §5 DDL 的 health_logs 資料表。
 * 🔒 欄位名稱/型態/限制完全照題目，不可變動。
 */
@Entity
@Table(name = "health_logs")
public class HealthLog {

    /** id INTEGER PRIMARY KEY AUTOINCREMENT */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** log_date DATE NOT NULL — 紀錄日期（YYYY-MM-DD） */
    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    /** sleep_hours REAL NOT NULL — 睡眠時數，例如 7.5 */
    @Column(name = "sleep_hours", nullable = false)
    private Double sleepHours;

    /** steps INTEGER NOT NULL — 當日步數 */
    @Column(name = "steps", nullable = false)
    private Integer steps;

    /** mood_score INTEGER NOT NULL — 心情分數 1~10 */
    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    /**
     * risk_level TEXT — 低/中/高。
     * ⚠️ 由決策樹即時計算後寫回（§5 註：可選擇是否持久化，此處選擇持久化以利歷史檢視）。
     * 種子資料不預先給值。
     */
    @Column(name = "risk_level")
    private String riskLevel;

    /** 資料歸屬使用者（§9 多使用者隔離；新增欄位，不改動 §5 既有欄位） */
    @Column(name = "user_id")
    private Long userId;

    public HealthLog() {
    }

    public HealthLog(LocalDate logDate, Double sleepHours, Integer steps, Integer moodScore) {
        this.logDate = logDate;
        this.sleepHours = sleepHours;
        this.steps = steps;
        this.moodScore = moodScore;
    }

    // ---- getters / setters ----
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getLogDate() { return logDate; }
    public void setLogDate(LocalDate logDate) { this.logDate = logDate; }

    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double sleepHours) { this.sleepHours = sleepHours; }

    public Integer getSteps() { return steps; }
    public void setSteps(Integer steps) { this.steps = steps; }

    public Integer getMoodScore() { return moodScore; }
    public void setMoodScore(Integer moodScore) { this.moodScore = moodScore; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
