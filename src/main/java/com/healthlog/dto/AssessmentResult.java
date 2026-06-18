package com.healthlog.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 綜合健康評估結果 DTO（§4 更有效評估）。
 * 整合決策樹風險 + 身體數據(BMI) + 生命徵象(血壓/血糖/心率/體溫) + 水分,
 * 產出各項判讀、綜合健康分數與健康建議；亦為 PDF 報告書資料來源。
 */
public class AssessmentResult {

    public LocalDate asOfDate;

    // 決策樹風險（沿用 §6 核心）
    public String decisionTreeRisk;     // 低/中/高
    public RiskResult riskDetail;       // 含 reasoning 與 decisionPath

    // 各項指標判讀
    public Metric bmi;
    public Metric bloodPressure;
    public Metric bloodSugar;
    public Metric heartRate;
    public Metric bodyTemp;
    public Metric water;

    // 綜合健康分數
    public int compositeScore;          // 0~100
    public String scoreGrade;           // 優/良/普通/需注意
    public List<String> recommendations; // 健康建議

    /** 單一指標的判讀：數值 + 等級 + 文字說明 */
    public static class Metric {
        public String name;
        public String value;     // 顯示用數值（含單位）
        public String level;     // 正常/偏高/過低/異常...
        public String status;    // good / warn / danger（前端上色）
        public String note;      // 判讀說明

        public Metric(String name, String value, String level, String status, String note) {
            this.name = name;
            this.value = value;
            this.level = level;
            this.status = status;
            this.note = note;
        }
    }
}
