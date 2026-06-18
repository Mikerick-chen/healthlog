package com.healthlog.service;

import com.healthlog.dto.RoiResult;
import com.healthlog.dto.RoiResult.Item;
import com.healthlog.entity.BodyMetric;
import com.healthlog.entity.HealthLog;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * 「健康投資回報率（ROI）」服務（§2）。
 *
 * 比較本週 vs 上週：行為（喝水、步數）投入了多少，結果（體重、靜止心率）改善了多少，
 * 把抽象努力轉成具象成就感，例如：「本週多走 1.2 萬步、多喝 3000ml → 體重下降 0.8%」。
 */
@Service
public class RoiService {

    private final HealthLogRepository healthRepo;
    private final BodyMetricRepository bodyRepo;
    private final VitalSignRepository vitalRepo;

    public RoiService(HealthLogRepository healthRepo, BodyMetricRepository bodyRepo, VitalSignRepository vitalRepo) {
        this.healthRepo = healthRepo;
        this.bodyRepo = bodyRepo;
        this.vitalRepo = vitalRepo;
    }

    public RoiResult compute() {
        Long uid = CurrentUser.id();
        RoiResult res = new RoiResult();
        LocalDate today = LocalDate.now();
        LocalDate thisStart = today.minusDays(6);
        LocalDate lastEnd = thisStart.minusDays(1);
        LocalDate lastStart = lastEnd.minusDays(6);
        res.thisWeekLabel = thisStart + " ~ " + today;
        res.lastWeekLabel = lastStart + " ~ " + lastEnd;

        List<HealthLog> logsThis = healthRepo.findByUserIdAndLogDateBetweenOrderByLogDateAsc(uid, thisStart, today);
        List<HealthLog> logsLast = healthRepo.findByUserIdAndLogDateBetweenOrderByLogDateAsc(uid, lastStart, lastEnd);
        List<BodyMetric> bodyThis = bodyRepo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(uid, thisStart, today);
        List<BodyMetric> bodyLast = bodyRepo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(uid, lastStart, lastEnd);
        List<VitalSign> vitalThis = vitalRepo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(uid, thisStart, today);
        List<VitalSign> vitalLast = vitalRepo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(uid, lastStart, lastEnd);

        res.enoughData = !logsThis.isEmpty() || !bodyThis.isEmpty() || !vitalThis.isEmpty();

        // 行為：步數、喝水（越多越好）
        res.behaviors.add(item("平均步數", "步", true,
                avg(logsThis, h -> toD(h.getSteps())), avg(logsLast, h -> toD(h.getSteps()))));
        res.behaviors.add(item("平均喝水", "ml", true,
                avg(bodyThis, b -> toD(b.getWaterMl())), avg(bodyLast, b -> toD(b.getWaterMl()))));
        res.behaviors.add(item("平均睡眠", "hr", true,
                avg(logsThis, HealthLog::getSleepHours), avg(logsLast, HealthLog::getSleepHours)));

        // 結果：體重、靜止心率、收縮壓（越低越好）
        res.outcomes.add(item("平均體重", "kg", false,
                avg(bodyThis, BodyMetric::getWeightKg), avg(bodyLast, BodyMetric::getWeightKg)));
        res.outcomes.add(item("平均靜止心率", "bpm", false,
                avg(vitalThis, v -> toD(v.getHeartRate())), avg(vitalLast, v -> toD(v.getHeartRate()))));
        res.outcomes.add(item("平均收縮壓", "mmHg", false,
                avg(vitalThis, v -> toD(v.getSystolic())), avg(vitalLast, v -> toD(v.getSystolic()))));

        buildHighlights(res);
        return res;
    }

    private void buildHighlights(RoiResult res) {
        // 找出有改善的行為與結果，組成白話敘述
        Item steps = find(res.behaviors, "平均步數");
        Item water = find(res.behaviors, "平均喝水");
        Item weight = find(res.outcomes, "平均體重");
        Item hr = find(res.outcomes, "平均靜止心率");

        if (steps != null && steps.delta != null && steps.delta > 0
                && weight != null && weight.delta != null && weight.delta < 0) {
            res.highlights.add(String.format("本週平均多走 %.0f 步，體重下降 %.1f%%（%.1fkg），努力有回報！",
                    steps.delta, Math.abs(weight.deltaPct), Math.abs(weight.delta)));
        }
        if (water != null && water.delta != null && water.delta > 0
                && hr != null && hr.delta != null && hr.delta < 0) {
            res.highlights.add(String.format("本週多喝 %.0f ml 水，靜止心率下降 %.0f bpm，循環更穩定。",
                    water.delta, Math.abs(hr.delta)));
        }
        if (res.highlights.isEmpty()) {
            if (steps != null && steps.delta != null && steps.delta > 0)
                res.highlights.add(String.format("本週步數比上週多 %.0f 步，保持下去結果會慢慢顯現。", steps.delta));
            else
                res.highlights.add("持續記錄行為與結果，系統就能算出你的健康投資回報。");
        }
    }

    private Item find(List<Item> list, String name) {
        return list.stream().filter(i -> i.name.equals(name)).findFirst().orElse(null);
    }

    private Item item(String name, String unit, boolean betterWhenUp, Double thisAvg, Double lastAvg) {
        Item it = new Item(name, unit, betterWhenUp);
        it.thisWeek = round(thisAvg);
        it.lastWeek = round(lastAvg);
        if (thisAvg != null && lastAvg != null) {
            it.delta = round(thisAvg - lastAvg);
            it.deltaPct = lastAvg == 0 ? null : round((thisAvg - lastAvg) / Math.abs(lastAvg) * 100);
            boolean improved = betterWhenUp ? it.delta > 0 : it.delta < 0;
            it.status = it.delta == 0 ? "neutral" : (improved ? "good" : "warn");
        } else {
            it.status = "neutral";
        }
        return it;
    }

    private <T> Double avg(List<T> list, Function<T, Double> getter) {
        var vals = list.stream().map(getter).filter(java.util.Objects::nonNull).mapToDouble(d -> d).toArray();
        if (vals.length == 0) return null;
        double s = 0; for (double v : vals) s += v; return s / vals.length;
    }

    private Double toD(Integer i) { return i == null ? null : i.doubleValue(); }
    private Double round(Double v) { return v == null ? null : Math.round(v * 10.0) / 10.0; }
}
