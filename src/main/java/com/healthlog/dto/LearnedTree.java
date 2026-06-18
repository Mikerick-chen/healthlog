package com.healthlog.dto;

import java.util.Map;

/**
 * 由資料學習出的決策樹（A1 遞迴樹建構器的輸出，可序列化供前端視覺化）。
 */
public class LearnedTree {

    public String criterion;        // info_gain / gain_ratio / gini（A2）
    public int sampleCount;
    public double trainingAccuracy; // 訓練資料準確率（%）
    public int maxDepth;
    public int minSamplesLeaf;
    public Node root;

    /** 樹節點：內部節點有 feature/threshold/children；葉節點有 label。 */
    public static class Node {
        public boolean leaf;
        public String feature;        // sleep_hours / steps / mood_score（內部名）
        public String featureLabel;   // 睡眠 / 步數 / 心情（顯示名）
        public Double threshold;       // 分割門檻（< 走左、≥ 走右）
        public String label;           // 葉節點：風險等級（多數類）
        public int samples;            // 落入此節點的樣本數
        public Double impurity;        // 不純度（熵或 Gini）
        public Double splitScore;      // 此節點分割的準則分數（資訊增益/增益比/Gini 下降）
        public Map<String, Integer> distribution; // 各類別樣本數
        public Node left;              // < threshold
        public Node right;             // ≥ threshold
    }
}
