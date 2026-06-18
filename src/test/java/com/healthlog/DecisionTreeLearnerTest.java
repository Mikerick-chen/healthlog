package com.healthlog;

import com.healthlog.dto.LearnedTree;
import com.healthlog.service.DecisionTreeLearner;
import com.healthlog.service.DecisionTreeLearner.Criterion;
import com.healthlog.service.InformationGainService.Sample;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A1 遞迴樹學習器 + A2 多準則測試。
 */
class DecisionTreeLearnerTest {

    private final DecisionTreeLearner learner = new DecisionTreeLearner();

    private List<Sample> separableData() {
        List<Sample> s = new ArrayList<>();
        for (int i = 0; i < 6; i++) s.add(new Sample(4.0 + i * 0.1, 1500 + i * 100, 2, "高"));
        for (int i = 0; i < 6; i++) s.add(new Sample(6.0 + i * 0.05, 4500 + i * 100, 5, "中"));
        for (int i = 0; i < 6; i++) s.add(new Sample(7.5 + i * 0.1, 8000 + i * 100, 8, "低"));
        return s;
    }

    @Test
    void A1_可分資料應學成多層樹且高準確率() {
        LearnedTree t = learner.learn(separableData(), Criterion.GAIN_RATIO, 4, 1);
        assertFalse(t.root.leaf, "根節點不應是葉（應為多層分支）");
        assertNotNull(t.root.feature, "內部節點應有分割特徵");
        assertTrue(t.trainingAccuracy >= 95.0, "可分資料訓練準確率應很高，實得=" + t.trainingAccuracy);
    }

    @Test
    void A2_三種準則皆可運作() {
        for (Criterion c : Criterion.values()) {
            LearnedTree t = learner.learn(separableData(), c, 4, 1);
            assertEquals(c.name().toLowerCase(), t.criterion);
            assertTrue(t.trainingAccuracy >= 95.0, c + " 準確率應高，實得=" + t.trainingAccuracy);
        }
    }

    @Test
    void 剪枝_最大深度1只切一層() {
        LearnedTree t = learner.learn(separableData(), Criterion.INFO_GAIN, 1, 1);
        assertFalse(t.root.leaf);
        assertTrue(t.root.left.leaf && t.root.right.leaf, "深度上限 1，子節點應為葉");
    }
}
