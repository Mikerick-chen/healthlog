package com.healthlog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 動態專屬基準線結果（§3）。
 * 以使用者「自己的長期數據」算出專屬正常波動範圍，偏離才警示。
 */
public class BaselineResult {

    public int sampleDays;
    public List<MetricBaseline> metrics = new ArrayList<>();
    public List<String> alerts = new ArrayList<>(); // 偏離專屬常態的提醒

    public static class MetricBaseline {
        public String name;        // 指標名
        public String unit;        // 單位
        public Double mean;        // 個人平均
        public Double std;         // 標準差
        public Double low;         // 專屬正常下限（mean - k*std）
        public Double high;        // 專屬正常上限（mean + k*std）
        public Double latest;      // 最新值
        public Double zScore;      // 標準分數
        public String status;      // good / warn / danger
        public String note;        // 文字判讀（偏高/偏低/正常）

        public MetricBaseline(String name, String unit) { this.name = name; this.unit = unit; }
    }
}
