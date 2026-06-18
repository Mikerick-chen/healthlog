package com.healthlog.service;

import com.healthlog.dto.LearnedTree;
import com.healthlog.dto.LearnedTree.Node;
import com.healthlog.service.InformationGainService.Sample;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.ToDoubleFunction;

/**
 * ⭐ A1 決策樹學習器（ID3/CART 式遞迴建構）＋ A2 多種分割準則。
 *
 * 不再寫死「睡眠→步數→心情」，而是每個節點都用資訊增益／增益比／Gini 自動挑
 * 「最佳特徵 + 最佳門檻」，遞迴長出多層樹（仍為多層分支，符合硬規範），並可剪枝
 * （max_depth / min_samples_leaf）避免過擬合。輸出可序列化樹供前端視覺化與比對。
 *
 * 註：本學習器用於分析/透明化展示；正式分類仍由 {@link DecisionTreeService} 的
 * 既定結構樹擔任（保留 §6.2 結構與「步數正常但心情差」中間情況之可解釋性）。
 */
@Service
public class DecisionTreeLearner {

    /** A2：分割準則 */
    public enum Criterion { INFO_GAIN, GAIN_RATIO, GINI }

    /** 一個特徵：內部名、顯示名、取值函式 */
    private record Feat(String name, String label, ToDoubleFunction<Sample> get) {}

    private static final List<Feat> FEATURES = List.of(
            new Feat("sleep_hours", "睡眠", Sample::sleepHours),
            new Feat("steps", "步數", s -> s.steps()),
            new Feat("mood_score", "心情", s -> s.moodScore()));

    /** 最佳分割暫存 */
    private record Split(Feat feat, double threshold, double score) {}

    public LearnedTree learn(List<Sample> samples, Criterion criterion, int maxDepth, int minSamplesLeaf) {
        LearnedTree t = new LearnedTree();
        t.criterion = criterion.name().toLowerCase();
        t.sampleCount = samples.size();
        t.maxDepth = maxDepth;
        t.minSamplesLeaf = minSamplesLeaf;
        t.root = build(samples, criterion, 0, maxDepth, minSamplesLeaf);
        t.trainingAccuracy = round(accuracy(samples, t.root));
        return t;
    }

    private Node build(List<Sample> samples, Criterion criterion, int depth, int maxDepth, int minLeaf) {
        Node node = new Node();
        node.samples = samples.size();
        node.distribution = countLabels(samples);
        node.impurity = round(impurity(samples, criterion));
        String majority = majorityLabel(node.distribution);

        // 停止條件：純節點 / 達最大深度 / 樣本太少無法再分
        if (node.impurity == 0.0 || depth >= maxDepth || samples.size() < 2 * minLeaf) {
            node.leaf = true;
            node.label = majority;
            return node;
        }

        Split best = bestSplit(samples, criterion, minLeaf);
        if (best == null || best.score() <= 0.0) {
            node.leaf = true;
            node.label = majority;
            return node;
        }

        node.feature = best.feat().name();
        node.featureLabel = best.feat().label();
        node.threshold = round(best.threshold());
        node.splitScore = round(best.score());

        List<Sample> left = new ArrayList<>();
        List<Sample> right = new ArrayList<>();
        for (Sample s : samples) {
            if (best.feat().get().applyAsDouble(s) < best.threshold()) left.add(s);
            else right.add(s);
        }
        node.left = build(left, criterion, depth + 1, maxDepth, minLeaf);
        node.right = build(right, criterion, depth + 1, maxDepth, minLeaf);
        return node;
    }

    /** 掃描所有特徵與候選門檻，挑準則分數最高者 */
    private Split bestSplit(List<Sample> samples, Criterion criterion, int minLeaf) {
        double parent = impurity(samples, criterion);
        Split best = null;
        for (Feat f : FEATURES) {
            double[] values = samples.stream().mapToDouble(f.get()).distinct().sorted().toArray();
            for (int i = 0; i + 1 < values.length; i++) {
                double threshold = (values[i] + values[i + 1]) / 2.0;
                List<Sample> left = new ArrayList<>();
                List<Sample> right = new ArrayList<>();
                for (Sample s : samples) {
                    if (f.get().applyAsDouble(s) < threshold) left.add(s); else right.add(s);
                }
                if (left.size() < minLeaf || right.size() < minLeaf) continue;
                double score = score(criterion, parent, samples.size(), left, right);
                if (best == null || score > best.score()) best = new Split(f, threshold, score);
            }
        }
        return best;
    }

    /** 依準則計算分割分數（資訊增益 / 增益比 / Gini 下降） */
    private double score(Criterion criterion, double parentImpurity, int n, List<Sample> left, List<Sample> right) {
        double wl = (double) left.size() / n, wr = (double) right.size() / n;
        double childImpurity = wl * impurity(left, criterion) + wr * impurity(right, criterion);
        double gain = parentImpurity - childImpurity;
        if (criterion == Criterion.GAIN_RATIO) {
            // 增益比 = 資訊增益 / 分割資訊（避免偏好多值切分）
            double splitInfo = -(wl == 0 ? 0 : wl * log2(wl)) - (wr == 0 ? 0 : wr * log2(wr));
            return splitInfo == 0 ? 0 : gain / splitInfo;
        }
        return gain; // INFO_GAIN 與 GINI 皆為「下降量」
    }

    /** 節點不純度：INFO_GAIN/GAIN_RATIO 用熵；GINI 用 Gini 係數 */
    private double impurity(List<Sample> samples, Criterion criterion) {
        return criterion == Criterion.GINI ? gini(samples) : entropy(samples);
    }

    private double entropy(List<Sample> samples) {
        if (samples.isEmpty()) return 0;
        Map<String, Integer> c = countLabels(samples);
        double n = samples.size(), h = 0;
        for (int v : c.values()) { double p = v / n; h -= p * log2(p); }
        return h;
    }

    private double gini(List<Sample> samples) {
        if (samples.isEmpty()) return 0;
        Map<String, Integer> c = countLabels(samples);
        double n = samples.size(), sum = 0;
        for (int v : c.values()) { double p = v / n; sum += p * p; }
        return 1 - sum;
    }

    private Map<String, Integer> countLabels(List<Sample> samples) {
        Map<String, Integer> c = new LinkedHashMap<>();
        for (Sample s : samples) c.merge(s.label(), 1, Integer::sum);
        return c;
    }

    private String majorityLabel(Map<String, Integer> dist) {
        return dist.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("中");
    }

    /** 用學成的樹預測，回傳訓練準確率（%） */
    private double accuracy(List<Sample> samples, Node root) {
        if (samples.isEmpty()) return 0;
        int correct = 0;
        for (Sample s : samples) if (predict(root, s).equals(s.label())) correct++;
        return correct * 100.0 / samples.size();
    }

    private String predict(Node node, Sample s) {
        if (node.leaf) return node.label;
        double v = FEATURES.stream().filter(f -> f.name().equals(node.feature)).findFirst()
                .orElseThrow().get().applyAsDouble(s);
        return predict(v < node.threshold ? node.left : node.right, s);
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }

    private static double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
