package com.healthlog;

import com.healthlog.dto.RiskResult;
import com.healthlog.service.DecisionTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 決策樹多分支測試（§10.4 炫項：覆蓋多種分支路徑，尤其「步數正常但心情差」中間情況）。
 * 使用固定門檻 T1=6.0、T2=5000、T3=5 以便斷言。
 */
class DecisionTreeServiceTest {

    private DecisionTreeService tree;

    @BeforeEach
    void setUp() {
        tree = new DecisionTreeService();
        tree.applyThresholds(6.0, 5000, 5);
    }

    @Test
    void 睡眠少_步數少_心情差_判為高() { // T1 測試案例
        RiskResult r = tree.classify(1L, 4.5, 2000, 3);
        assertEquals("高", r.getRiskLevel());
    }

    @Test
    void 睡眠足_步數多_心情好_判為低() { // T2 測試案例
        RiskResult r = tree.classify(2L, 8.0, 9000, 8);
        assertEquals("低", r.getRiskLevel());
    }

    @Test
    void 睡眠少_步數正常但心情差_判為中() { // ⭐ T3 中間情況
        RiskResult r = tree.classify(3L, 5.0, 7000, 2);
        assertEquals("中", r.getRiskLevel());
        // 決策路徑應點出「中間情況」
        assertTrue(r.getDecisionPath().stream().anyMatch(p -> p.contains("中間情況")));
    }

    @Test
    void 睡眠足_步數少_心情好_判為低() {
        RiskResult r = tree.classify(4L, 7.5, 3000, 7);
        assertEquals("低", r.getRiskLevel());
    }

    @Test
    void 睡眠足_步數少_心情差_判為中() {
        RiskResult r = tree.classify(5L, 7.5, 3000, 2);
        assertEquals("中", r.getRiskLevel());
    }

    @Test
    void 睡眠少_步數少_心情好_判為中() {
        RiskResult r = tree.classify(6L, 4.0, 2000, 8);
        assertEquals("中", r.getRiskLevel());
    }

    @Test
    void 決策路徑為多層_至少三步() {
        RiskResult r = tree.classify(7L, 4.5, 2000, 3);
        assertTrue(r.getDecisionPath().size() >= 3, "決策樹應為多層分支，路徑至少三步");
    }

    @Test
    void A4_未校準時信心值為null() {
        RiskResult r = tree.classify(8L, 4.5, 2000, 3);
        assertNull(r.getConfidence(), "未呼叫 calibrateConfidence 前信心值應為 null");
    }

    @Test
    void A4_校準後純葉節點信心為100() {
        // 高風險葉(A)：兩筆皆標籤「高」→ 純度 100%
        tree.calibrateConfidence(java.util.List.of(
                new com.healthlog.service.InformationGainService.Sample(4.5, 2000, 3, "高"),
                new com.healthlog.service.InformationGainService.Sample(4.0, 1500, 2, "高"),
                new com.healthlog.service.InformationGainService.Sample(8.0, 9000, 8, "低"),
                new com.healthlog.service.InformationGainService.Sample(8.0, 9500, 9, "低")));
        RiskResult r = tree.classify(9L, 4.5, 2000, 3);
        assertEquals("高", r.getRiskLevel());
        assertEquals(100.0, r.getConfidence(), "純葉節點信心應為 100%");
    }
}
