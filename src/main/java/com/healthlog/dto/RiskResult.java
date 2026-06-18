package com.healthlog.dto;

import java.util.List;

/**
 * 決策樹風險判定結果 DTO。
 * 對應 §7 GET /health-logs/risk 回傳格式，並加值回傳 reasoning 與決策路徑（§10.1 炫項）。
 */
public class RiskResult {

    private Long logId;
    private String riskLevel;          // 低 / 中 / 高
    private Reasoning reasoning;        // 判斷依據（三特徵值）
    private List<String> decisionPath;  // 決策樹走過的每一步（視覺化用）
    private Double confidence;          // 葉節點信心值 0~100（依訓練資料純度，A4）；未校準時為 null

    public RiskResult() {}

    public RiskResult(Long logId, String riskLevel, Reasoning reasoning, List<String> decisionPath) {
        this.logId = logId;
        this.riskLevel = riskLevel;
        this.reasoning = reasoning;
        this.decisionPath = decisionPath;
    }

    /** reasoning 子物件：把決策樹判斷依據一併回傳，前端可解釋「為什麼是高風險」 */
    public static class Reasoning {
        private double sleepHours;
        private int steps;
        private int moodScore;

        public Reasoning(double sleepHours, int steps, int moodScore) {
            this.sleepHours = sleepHours;
            this.steps = steps;
            this.moodScore = moodScore;
        }
        public double getSleepHours() { return sleepHours; }
        public int getSteps() { return steps; }
        public int getMoodScore() { return moodScore; }
    }

    public Long getLogId() { return logId; }
    public String getRiskLevel() { return riskLevel; }
    public Reasoning getReasoning() { return reasoning; }
    public List<String> getDecisionPath() { return decisionPath; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}
