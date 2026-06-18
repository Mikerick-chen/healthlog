package com.healthlog.service;

import com.healthlog.dto.BaselineResult;
import com.healthlog.dto.BaselineResult.MetricBaseline;
import com.healthlog.entity.BodyMetric;
import com.healthlog.entity.HealthLog;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 「動態專屬基準線」服務（§3）。
 *
 * 拋棄大眾化死板標準，改用每位使用者「自己的長期數據」計算平均(mean)與標準差(std)，
 * 形成專屬正常波動範圍 [mean - k·std, mean + k·std]。
 * 只有當最新值的標準分數(z-score)超出範圍時才觸發專屬警示。
 */
@Service
public class BaselineService {

    private static final double K = 1.5;          // 正常範圍寬度（±1.5 標準差）
    private static final int MIN_SAMPLES = 5;     // 樣本太少不做基準（避免誤判）

    private final HealthLogRepository healthRepo;
    private final BodyMetricRepository bodyRepo;
    private final VitalSignRepository vitalRepo;

    public BaselineService(HealthLogRepository healthRepo, BodyMetricRepository bodyRepo, VitalSignRepository vitalRepo) {
        this.healthRepo = healthRepo;
        this.bodyRepo = bodyRepo;
        this.vitalRepo = vitalRepo;
    }

    public BaselineResult compute() {
        Long uid = CurrentUser.id();
        BaselineResult res = new BaselineResult();

        List<HealthLog> logs = healthRepo.findByUserIdOrderByLogDateDesc(uid);
        List<BodyMetric> bodies = bodyRepo.findByUserIdOrderByRecordDateDesc(uid);
        List<VitalSign> vitals = vitalRepo.findByUserIdOrderByRecordDateDesc(uid);
        res.sampleDays = Math.max(logs.size(), Math.max(bodies.size(), vitals.size()));

        // 健康日誌類（注意：睡眠/步數/心情「偏低」才是壞，方向會影響提醒語氣）
        addMetric(res, "睡眠時數", "hr", logs, HealthLog::getSleepHours, true);
        addMetric(res, "步數", "步", logs, h -> h.getSteps() == null ? null : h.getSteps().doubleValue(), true);
        addMetric(res, "心情", "分", logs, h -> h.getMoodScore() == null ? null : h.getMoodScore().doubleValue(), true);
        // 身體數據
        addMetric(res, "體重", "kg", bodies, BodyMetric::getWeightKg, false);
        addMetric(res, "喝水量", "ml", bodies, b -> b.getWaterMl() == null ? null : b.getWaterMl().doubleValue(), true);
        // 生命徵象
        addMetric(res, "收縮壓", "mmHg", vitals, v -> v.getSystolic() == null ? null : v.getSystolic().doubleValue(), false);
        addMetric(res, "舒張壓", "mmHg", vitals, v -> v.getDiastolic() == null ? null : v.getDiastolic().doubleValue(), false);
        addMetric(res, "心率", "bpm", vitals, v -> v.getHeartRate() == null ? null : v.getHeartRate().doubleValue(), false);
        addMetric(res, "血糖", "mg/dL", vitals, v -> v.getBloodSugar() == null ? null : v.getBloodSugar().doubleValue(), false);

        if (res.alerts.isEmpty()) res.alerts.add("各項指標都在你的專屬正常範圍內，狀態穩定。");
        return res;
    }

    /**
     * @param higherIsBetter 該指標是否「越高越好」（睡眠/步數/心情/喝水為 true；血壓/血糖/體重為 false）
     */
    private <T> void addMetric(BaselineResult res, String name, String unit,
                               List<T> records, Function<T, Double> getter, boolean higherIsBetter) {
        List<Double> series = new ArrayList<>();
        for (T r : records) {
            Double v = getter.apply(r);
            if (v != null) series.add(v);
        }
        if (series.size() < MIN_SAMPLES) return; // 資料不足，跳過

        double mean = series.stream().mapToDouble(d -> d).average().orElse(0);
        double var = series.stream().mapToDouble(d -> (d - mean) * (d - mean)).average().orElse(0);
        double std = Math.sqrt(var);

        MetricBaseline mb = new MetricBaseline(name, unit);
        mb.mean = round(mean);
        mb.std = round(std);
        mb.low = round(mean - K * std);
        mb.high = round(mean + K * std);
        // 最新值＝series 第一筆（repo 已 desc）
        double latest = series.get(0);
        mb.latest = round(latest);
        mb.zScore = std == 0 ? 0.0 : round((latest - mean) / std);

        boolean above = latest > mb.high;
        boolean below = latest < mb.low;
        if (!above && !below) {
            mb.status = "good";
            mb.note = "在你的專屬正常範圍內";
        } else {
            // 偏離方向 + 是否為不利方向
            boolean unfavorable = higherIsBetter ? below : above;
            mb.status = (Math.abs(mb.zScore) >= 2.5) ? "danger" : "warn";
            mb.note = (above ? "高於" : "低於") + "你的常態（" + (unfavorable ? "需留意" : "偏離但方向尚可") + "）";
            String arrow = above ? "偏高" : "偏低";
            res.alerts.add(name + " " + mb.latest + unit + " " + arrow + "，超出你的專屬範圍 "
                    + mb.low + "～" + mb.high + unit + "（z=" + mb.zScore + "）");
        }
        res.metrics.add(mb);
    }

    private static Double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
