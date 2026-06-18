package com.healthlog.service;

import com.healthlog.dto.RiskResult;
import com.healthlog.entity.HealthLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐⭐⭐ 決策樹風險判定服務（本題最核心）。
 *
 * 🔒 結構為「多層分支」：先判睡眠 → 再判步數 → 再判心情，
 *     絕非單一 if，也非加權平均算分。
 *
 * 門檻值 T1/T2/T3 由 {@link InformationGainService} 對種子資料實測資訊增益決定，
 * 啟動時計算並回填（見 SeedDataService）。此處提供保底直覺值，確保未跑分析也可運作。
 *
 * 決策樹結構（依 §6.2，門檻以實測為準）：
 * 第一層 睡眠 sleep_hours
 *  ├─ < T1（睡眠不足）
 *  │    └─ 步數 steps
 *  │        ├─ < T2（步數也少）→ 心情 mood
 *  │        │     ├─ < T3 → 高
 *  │        │     └─ ≥ T3 → 中
 *  │        └─ ≥ T2（步數正常）→ 心情 mood
 *  │              ├─ < T3 → 中  ⭐【步數正常但心情差】中間情況（§5.2 檢查點）
 *  │              └─ ≥ T3 → 低
 *  └─ ≥ T1（睡眠足夠）
 *       └─ 步數 steps
 *           ├─ < T2 → 心情 mood
 *           │     ├─ < T3 → 中
 *           │     └─ ≥ T3 → 低
 *           └─ ≥ T2 → 低
 */
@Service
public class DecisionTreeService {

    private static final Logger log = LoggerFactory.getLogger(DecisionTreeService.class);

    // 保底直覺值（§6.3 保底機制）；啟動後會被資訊增益實測值覆寫
    private volatile double t1Sleep = 6.0;   // 睡眠門檻（小時）
    private volatile int    t2Steps = 5000;  // 步數門檻
    private volatile int    t3Mood  = 5;     // 心情門檻

    /** 由 InformationGainService 實測後回填門檻值 */
    public void applyThresholds(double t1Sleep, int t2Steps, int t3Mood) {
        this.t1Sleep = t1Sleep;
        this.t2Steps = t2Steps;
        this.t3Mood = t3Mood;
        log.info("決策樹門檻已套用實測值：T1(睡眠)={}hr, T2(步數)={}步, T3(心情)={}分",
                t1Sleep, t2Steps, t3Mood);
    }

    public double getT1Sleep() { return t1Sleep; }
    public int getT2Steps() { return t2Steps; }
    public int getT3Mood() { return t3Mood; }

    /**
     * 核心：依三特徵走決策樹，回傳風險等級 + 走過的路徑（可視覺化）。
     */
    public RiskResult classify(Long logId, double sleepHours, int steps, int moodScore) {
        List<String> path = new ArrayList<>();
        String risk;

        // 第一層：睡眠
        if (sleepHours < t1Sleep) {
            path.add(String.format("睡眠 %.1f hr < %.1f → 睡眠不足", sleepHours, t1Sleep));
            // 第二層：步數
            if (steps < t2Steps) {
                path.add(String.format("步數 %d < %d → 活動量低", steps, t2Steps));
                // 第三層：心情
                if (moodScore < t3Mood) {
                    path.add(String.format("心情 %d < %d → 情緒低落", moodScore, t3Mood));
                    risk = "高";
                } else {
                    path.add(String.format("心情 %d ≥ %d → 情緒尚可", moodScore, t3Mood));
                    risk = "中";
                }
            } else {
                path.add(String.format("步數 %d ≥ %d → 活動量正常", steps, t2Steps));
                if (moodScore < t3Mood) {
                    // ⭐ 中間情況：步數正常但心情差
                    path.add(String.format("心情 %d < %d → 步數正常但心情差（中間情況）", moodScore, t3Mood));
                    risk = "中";
                } else {
                    path.add(String.format("心情 %d ≥ %d → 情緒佳", moodScore, t3Mood));
                    risk = "低";
                }
            }
        } else {
            path.add(String.format("睡眠 %.1f hr ≥ %.1f → 睡眠足夠", sleepHours, t1Sleep));
            // 第二層：步數
            if (steps < t2Steps) {
                path.add(String.format("步數 %d < %d → 活動量低", steps, t2Steps));
                if (moodScore < t3Mood) {
                    path.add(String.format("心情 %d < %d → 情緒低落", moodScore, t3Mood));
                    risk = "中";
                } else {
                    path.add(String.format("心情 %d ≥ %d → 情緒尚可", moodScore, t3Mood));
                    risk = "低";
                }
            } else {
                path.add(String.format("步數 %d ≥ %d → 活動量正常", steps, t2Steps));
                path.add("睡眠足夠且步數正常 → 低風險");
                risk = "低";
            }
        }

        log.info("決策樹判定 logId={} 睡眠={} 步數={} 心情={} → 風險={}", logId, sleepHours, steps, moodScore, risk);
        return new RiskResult(logId, risk,
                new RiskResult.Reasoning(sleepHours, steps, moodScore), path);
    }

    /** 便利方法：直接對一筆 Entity 分類 */
    public RiskResult classify(HealthLog logEntity) {
        return classify(logEntity.getId(), logEntity.getSleepHours(),
                logEntity.getSteps(), logEntity.getMoodScore());
    }
}
