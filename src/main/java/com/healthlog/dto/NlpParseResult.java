package com.healthlog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * NLP 語意日誌解析結果（§1）。
 * 以中文關鍵字＋規則抽取，將白話文轉成結構化健康訊號（非外部 AI API）。
 */
public class NlpParseResult {

    public String originalText;
    public List<Tag> tags = new ArrayList<>();         // 抽取到的標籤（給前端做 chip / 圖表）
    public Extracted extracted = new Extracted();      // 可回填表單的數值
    public List<CategoryCount> categoryChart = new ArrayList<>(); // 各類別命中次數（視覺化）
    public String summary;                              // 一句話總結

    public static class Tag {
        public String category;   // 咖啡因 / 睡眠 / 運動 ...
        public String keyword;    // 命中的詞
        public String message;    // 健康意涵
        public String status;     // good / warn / danger / info
        public Tag(String category, String keyword, String message, String status) {
            this.category = category; this.keyword = keyword; this.message = message; this.status = status;
        }
    }

    public static class CategoryCount {
        public String category;
        public int count;
        public CategoryCount(String category, int count) { this.category = category; this.count = count; }
    }

    /** 抽取出的可量化資訊，可一鍵回填日誌/身體數據表單 */
    public static class Extracted {
        public Integer caffeineCups;   // 咖啡因杯數
        public Double sleepHours;      // 睡眠時數
        public Integer steps;          // 步數
        public Integer waterMl;        // 喝水量
        public Integer moodGuess;      // 推估心情 1-10
        public List<String> symptoms = new ArrayList<>(); // 症狀（可帶入診療室）
    }
}
