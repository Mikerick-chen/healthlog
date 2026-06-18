package com.healthlog.service;

import com.healthlog.dto.AnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * ⭐ 資訊增益（Information Gain）計算服務（§6.3 進階加分項）。
 *
 * 用途：
 *  1. 啟動時對種子資料（以三組設計分布為標籤）計算各特徵的資訊增益，
 *     決定決策樹的門檻值 T1/T2/T3 與分支順序。
 *  2. 提供獨立可重跑的分析（GET /health-logs/analysis），展示計算過程。
 *
 * 公式：
 *  熵 H(S) = -Σ p_i · log2(p_i)
 *  資訊增益 IG(S, split) = H(S) - Σ (|S_v|/|S|) · H(S_v)
 */
@Service
public class InformationGainService {

    private static final Logger log = LoggerFactory.getLogger(InformationGainService.class);

    /** 一筆樣本：三特徵 + 標籤 */
    public record Sample(double sleepHours, int steps, int moodScore, String label) {}

    /** 三特徵分析後的最佳門檻彙整（供啟動時回填決策樹） */
    public record Thresholds(double sleepThreshold, int stepsThreshold, int moodThreshold,
                             String rootFeature) {}

    /** 計算一組標籤分布的熵（log2） */
    public double entropy(Collection<String> labels) {
        if (labels.isEmpty()) return 0.0;
        Map<String, Integer> counts = new HashMap<>();
        for (String l : labels) counts.merge(l, 1, Integer::sum);
        double n = labels.size();
        double h = 0.0;
        for (int c : counts.values()) {
            double p = c / n;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h;
    }

    /**
     * 對單一特徵，嘗試所有候選門檻（相鄰相異值的中點），算出每個的資訊增益，
     * 回傳該特徵的最佳門檻與完整候選清單。
     */
    private AnalysisResult.FeatureAnalysis analyzeFeature(
            String featureName, List<Sample> samples, java.util.function.ToDoubleFunction<Sample> getter) {

        double parentEntropy = entropy(samples.stream().map(Sample::label).toList());

        // 取相異特徵值排序，產生候選門檻（中點）
        double[] values = samples.stream().mapToDouble(getter).distinct().sorted().toArray();
        List<AnalysisResult.Candidate> candidates = new ArrayList<>();
        double bestThreshold = values.length > 0 ? values[0] : 0;
        double bestGain = -1;

        for (int i = 0; i + 1 < values.length; i++) {
            double threshold = (values[i] + values[i + 1]) / 2.0;
            List<String> left = new ArrayList<>();   // < threshold
            List<String> right = new ArrayList<>();  // >= threshold
            for (Sample s : samples) {
                if (getter.applyAsDouble(s) < threshold) left.add(s.label());
                else right.add(s.label());
            }
            double n = samples.size();
            double childEntropy = (left.size() / n) * entropy(left)
                    + (right.size() / n) * entropy(right);
            double gain = parentEntropy - childEntropy;
            candidates.add(new AnalysisResult.Candidate(round(threshold), round(gain)));
            if (gain > bestGain) {
                bestGain = gain;
                bestThreshold = threshold;
            }
        }
        return new AnalysisResult.FeatureAnalysis(featureName, round(bestThreshold), round(bestGain), candidates);
    }

    /**
     * 對整份樣本做完整分析：三特徵各自最佳門檻 + 依資訊增益排序。
     */
    public AnalysisResult analyze(List<Sample> samples, String labelSource) {
        double parentEntropy = entropy(samples.stream().map(Sample::label).toList());

        AnalysisResult.FeatureAnalysis sleep = analyzeFeature("sleep_hours", samples, Sample::sleepHours);
        AnalysisResult.FeatureAnalysis steps = analyzeFeature("steps", samples, s -> s.steps());
        AnalysisResult.FeatureAnalysis mood  = analyzeFeature("mood_score", samples, s -> s.moodScore());

        List<AnalysisResult.FeatureAnalysis> features = new ArrayList<>(List.of(sleep, steps, mood));
        // 依資訊增益由高到低排序
        features.sort((a, b) -> Double.compare(b.getBestInfoGain(), a.getBestInfoGain()));

        String root = features.get(0).getFeature();
        String summary = String.format(
                "母節點熵 H=%.4f。資訊增益排序：%s。資訊增益最高者作為第一層分支：%s。",
                round(parentEntropy),
                String.join("、", features.stream()
                        .map(f -> f.getFeature() + "(IG=" + f.getBestInfoGain() + ")").toList()),
                root);

        log.info("資訊增益分析（標籤來源={}）：{}", labelSource, summary);
        return new AnalysisResult(samples.size(), round(parentEntropy), features, root, labelSource, summary);
    }

    /**
     * 啟動時用：對種子資料（標籤=設計組別）計算，回傳決策樹要套用的門檻。
     *
     * ⭐ 嚴格依 §6.3 step 3：「選資訊增益最高的特徵作為第一層分支(T1)，
     *    對切出來的子集合重複此計算，決定第二層(T2)、第三層(T3)。」
     *
     * 為何要遞迴子集合：若三特徵都在「整份資料」上取最佳切點，會各自把最極端的
     * 高風險組切出來（IG 相同），導致中/低兩組無法區分（中會被併進低）。
     * 因此 T1 在全體上取（睡眠，§6.2 結構），T2/T3 改在「第一層切出的較混合子集合」
     * 上重新計算，才能找到區分「中 vs 低」的步數/心情門檻。
     */
    public Thresholds deriveThresholds(List<Sample> samples) {
        // 第一層：睡眠在全體上的最佳切分（§6.2 指定睡眠為根）
        AnalysisResult full = analyze(samples, "種子資料設計組別（高/中/低）");
        double sleepT = featureOf(full, "sleep_hours").getBestThreshold();

        // 依 T1 切成兩子集合，取「較混合（熵較高）」的子集合續算 T2/T3
        List<Sample> left = samples.stream().filter(s -> s.sleepHours() < sleepT).toList();
        List<Sample> right = samples.stream().filter(s -> s.sleepHours() >= sleepT).toList();
        List<Sample> sub = entropy(left.stream().map(Sample::label).toList())
                >= entropy(right.stream().map(Sample::label).toList()) ? left : right;
        log.info("第一層睡眠門檻 T1={}；左子集合 {} 筆、右子集合 {} 筆，續對較混合子集合（{} 筆）計算 T2/T3。",
                sleepT, left.size(), right.size(), sub.size());

        // 第二、三層：在子集合上重算步數、心情的最佳切點
        int stepsT = (int) Math.round(
                analyzeFeature("steps", sub, s -> s.steps()).getBestThreshold());
        int moodT = (int) Math.round(
                analyzeFeature("mood_score", sub, s -> s.moodScore()).getBestThreshold());

        // A7 生理護欄：把資訊增益選出的門檻夾在臨床合理區間，避免資料巧合切出無意義門檻。
        double sleepClamped = clamp(sleepT, 5.0, 7.0);
        int stepsClamped = (int) clamp(stepsT, 3000, 8000);
        int moodClamped = (int) clamp(moodT, 3, 7);
        if (sleepClamped != sleepT || stepsClamped != stepsT || moodClamped != moodT) {
            log.info("A7 生理護欄已修正門檻：睡眠 {}→{}、步數 {}→{}、心情 {}→{}（夾入合理區間）",
                    sleepT, sleepClamped, stepsT, stepsClamped, moodT, moodClamped);
        }

        log.info("由資訊增益實測導出門檻：T1(睡眠)={}, T2(步數)={}, T3(心情)={}；建議根特徵={}",
                sleepClamped, stepsClamped, moodClamped, full.getChosenRootFeature());
        return new Thresholds(sleepClamped, stepsClamped, moodClamped, full.getChosenRootFeature());
    }

    private AnalysisResult.FeatureAnalysis featureOf(AnalysisResult r, String name) {
        return r.getFeatures().stream()
                .filter(f -> f.getFeature().equals(name))
                .findFirst().orElseThrow();
    }

    private static double round(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    /** A7：把數值夾在 [lo, hi] 區間內 */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
