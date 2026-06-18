package com.healthlog.dto;

import java.util.List;

/**
 * 資訊增益分析結果 DTO（§6.3 / §10.4：把計算過程而非寫死結果交付）。
 */
public class AnalysisResult {

    private int sampleCount;
    private double parentEntropy;          // 母節點熵
    private List<FeatureAnalysis> features; // 各特徵分析（已依資訊增益由高到低排序）
    private String chosenRootFeature;      // 資訊增益最高 → 第一層分支特徵
    private String labelSource;            // 標籤來源說明
    private String summary;                // 文字結論

    public AnalysisResult(int sampleCount, double parentEntropy, List<FeatureAnalysis> features,
                          String chosenRootFeature, String labelSource, String summary) {
        this.sampleCount = sampleCount;
        this.parentEntropy = parentEntropy;
        this.features = features;
        this.chosenRootFeature = chosenRootFeature;
        this.labelSource = labelSource;
        this.summary = summary;
    }

    /** 單一特徵的最佳切分分析 */
    public static class FeatureAnalysis {
        private String feature;            // sleep_hours / steps / mood_score
        private double bestThreshold;      // 最佳門檻
        private double bestInfoGain;       // 對應資訊增益
        private List<Candidate> candidates; // 所有候選門檻與其資訊增益

        public FeatureAnalysis(String feature, double bestThreshold, double bestInfoGain,
                               List<Candidate> candidates) {
            this.feature = feature;
            this.bestThreshold = bestThreshold;
            this.bestInfoGain = bestInfoGain;
            this.candidates = candidates;
        }
        public String getFeature() { return feature; }
        public double getBestThreshold() { return bestThreshold; }
        public double getBestInfoGain() { return bestInfoGain; }
        public List<Candidate> getCandidates() { return candidates; }
    }

    /** 單一候選門檻 */
    public static class Candidate {
        private double threshold;
        private double infoGain;
        public Candidate(double threshold, double infoGain) {
            this.threshold = threshold;
            this.infoGain = infoGain;
        }
        public double getThreshold() { return threshold; }
        public double getInfoGain() { return infoGain; }
    }

    public int getSampleCount() { return sampleCount; }
    public double getParentEntropy() { return parentEntropy; }
    public List<FeatureAnalysis> getFeatures() { return features; }
    public String getChosenRootFeature() { return chosenRootFeature; }
    public String getLabelSource() { return labelSource; }
    public String getSummary() { return summary; }
}
