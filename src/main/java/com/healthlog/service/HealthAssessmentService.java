package com.healthlog.service;

import com.healthlog.dto.AssessmentResult;
import com.healthlog.dto.AssessmentResult.Metric;
import com.healthlog.dto.RiskResult;
import com.healthlog.entity.BodyMetric;
import com.healthlog.entity.HealthLog;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 綜合健康評估服務（§4）。
 * 依國健署/常見臨床參考值，對 BMI、血壓、血糖、心率、體溫、水分做判讀，
 * 並結合決策樹風險計算「綜合健康分數」與健康建議。
 *
 * 註：分類門檻為一般衛教參考值，非醫療診斷；報告書亦會標明免責聲明。
 */
@Service
public class HealthAssessmentService {

    private static final int WATER_GOAL_ML = 2000; // 每日建議飲水量（預設值，可調）

    private final HealthLogRepository healthLogRepo;
    private final BodyMetricRepository bodyRepo;
    private final VitalSignRepository vitalRepo;
    private final DecisionTreeService decisionTree;

    public HealthAssessmentService(HealthLogRepository healthLogRepo,
                                   BodyMetricRepository bodyRepo,
                                   VitalSignRepository vitalRepo,
                                   DecisionTreeService decisionTree) {
        this.healthLogRepo = healthLogRepo;
        this.bodyRepo = bodyRepo;
        this.vitalRepo = vitalRepo;
        this.decisionTree = decisionTree;
    }

    /**
     * 對目前使用者做綜合評估（§8「同日對齊」：以最新健康日誌日期為錨，
     * 身體數據/生命徵象取「該日期(含)以前最近一筆」，避免拿不同日期的數據混算）。
     */
    public AssessmentResult assessLatest() {
        Long uid = CurrentUser.id();
        AssessmentResult r = new AssessmentResult();
        r.recommendations = new ArrayList<>();

        // 1) 決策樹風險（§6 核心）＝錨點
        HealthLog log = healthLogRepo.findFirstByUserIdOrderByLogDateDescIdDesc(uid);
        LocalDate anchor = (log != null) ? log.getLogDate() : LocalDate.now();
        r.asOfDate = anchor;
        if (log != null) {
            RiskResult rr = decisionTree.classify(log);
            r.decisionTreeRisk = rr.getRiskLevel();
            r.riskDetail = rr;
        }

        // 2) BMI / 水分（同日對齊：錨點日(含)以前最近一筆身體數據）
        BodyMetric body = bodyRepo.findFirstByUserIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(uid, anchor);
        if (body == null) body = bodyRepo.findFirstByUserIdOrderByRecordDateDescIdDesc(uid);
        if (body != null) {
            r.bmi = assessBmi(body.getBmi());
            r.water = assessWater(body.getWaterMl());
        }

        // 3) 生命徵象（同日對齊）
        VitalSign v = vitalRepo.findFirstByUserIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(uid, anchor);
        if (v == null) v = vitalRepo.findFirstByUserIdOrderByRecordDateDescIdDesc(uid);
        if (v != null) {
            r.bloodPressure = assessBloodPressure(v.getSystolic(), v.getDiastolic());
            r.bloodSugar = assessBloodSugar(v.getBloodSugar(), v.getMeasureContext());
            r.heartRate = assessHeartRate(v.getHeartRate());
            r.bodyTemp = assessBodyTemp(v.getBodyTemp());
        }

        // 4) 綜合健康分數 + 建議
        computeScoreAndAdvice(r);
        return r;
    }

    // ---- 各項判讀（繁中註解，門檻為衛教參考值）----

    /** BMI：<18.5 過輕｜18.5–24 正常｜24–27 過重｜≥27 肥胖（國健署標準） */
    private Metric assessBmi(Double bmi) {
        if (bmi == null) return null;
        String level, status, note;
        if (bmi < 18.5) { level = "過輕"; status = "warn"; note = "體重偏輕，注意營養攝取。"; }
        else if (bmi < 24) { level = "正常"; status = "good"; note = "體位正常，請維持。"; }
        else if (bmi < 27) { level = "過重"; status = "warn"; note = "體重過重，建議控制飲食與運動。"; }
        else { level = "肥胖"; status = "danger"; note = "已達肥胖範圍，建議就醫諮詢與積極減重。"; }
        return new Metric("BMI", String.valueOf(bmi), level, status, note);
    }

    /** 血壓：正常<120/80；偏高120-129;一期130-139或80-89;二期≥140或≥90 */
    private Metric assessBloodPressure(Integer sys, Integer dia) {
        if (sys == null || dia == null) return null;
        String level, status, note;
        if (sys < 120 && dia < 80) { level = "正常"; status = "good"; note = "血壓正常。"; }
        else if (sys < 130 && dia < 80) { level = "血壓偏高"; status = "warn"; note = "血壓略高，注意生活作息。"; }
        else if (sys < 140 || dia < 90) { level = "高血壓一期"; status = "warn"; note = "高血壓一期，建議減鹽、規律量測。"; }
        else { level = "高血壓二期"; status = "danger"; note = "高血壓二期，建議盡快就醫。"; }
        return new Metric("血壓", sys + "/" + dia + " mmHg", level, status, note);
    }

    /** 血糖：空腹 正常<100/前期100-125/糖尿病≥126；飯後或隨機 正常<140/前期140-199/糖尿病≥200 */
    private Metric assessBloodSugar(Integer sugar, String context) {
        if (sugar == null) return null;
        boolean fasting = context == null || context.contains("空腹");
        String level, status, note;
        if (fasting) {
            if (sugar < 100) { level = "正常"; status = "good"; note = "空腹血糖正常。"; }
            else if (sugar < 126) { level = "糖尿病前期"; status = "warn"; note = "空腹血糖偏高，建議飲食控制。"; }
            else { level = "疑似糖尿病"; status = "danger"; note = "空腹血糖過高，建議就醫檢查。"; }
        } else {
            if (sugar < 140) { level = "正常"; status = "good"; note = "飯後/隨機血糖正常。"; }
            else if (sugar < 200) { level = "糖尿病前期"; status = "warn"; note = "血糖偏高，建議飲食控制。"; }
            else { level = "疑似糖尿病"; status = "danger"; note = "血糖過高，建議就醫檢查。"; }
        }
        String ctx = (context == null ? "" : "（" + context + "）");
        return new Metric("血糖", sugar + " mg/dL" + ctx, level, status, note);
    }

    /** 心率：60–100 正常；<60 過慢；>100 過快 */
    private Metric assessHeartRate(Integer hr) {
        if (hr == null) return null;
        String level, status, note;
        if (hr < 60) { level = "心搏過慢"; status = "warn"; note = "靜止心率偏低，若有不適請留意。"; }
        else if (hr <= 100) { level = "正常"; status = "good"; note = "心率正常。"; }
        else { level = "心搏過快"; status = "warn"; note = "心率偏高，注意休息與壓力。"; }
        return new Metric("心率", hr + " bpm", level, status, note);
    }

    /** 體溫：36–37.5 正常；<36 偏低；37.5–38 微燒；≥38 發燒 */
    private Metric assessBodyTemp(Double t) {
        if (t == null) return null;
        String level, status, note;
        if (t < 36.0) { level = "體溫偏低"; status = "warn"; note = "體溫偏低，注意保暖。"; }
        else if (t < 37.5) { level = "正常"; status = "good"; note = "體溫正常。"; }
        else if (t < 38.0) { level = "微燒"; status = "warn"; note = "體溫略高，多補充水分休息。"; }
        else { level = "發燒"; status = "danger"; note = "已發燒，建議就醫。"; }
        return new Metric("體溫", t + " ℃", level, status, note);
    }

    /** 水分達標率（相對每日建議 2000ml） */
    private Metric assessWater(Integer waterMl) {
        if (waterMl == null) return null;
        int pct = (int) Math.round(waterMl * 100.0 / WATER_GOAL_ML);
        String level, status, note;
        if (pct >= 100) { level = "已達標"; status = "good"; note = "飲水量充足。"; }
        else if (pct >= 70) { level = "略不足"; status = "warn"; note = "再補充一些水分即可達標。"; }
        else { level = "不足"; status = "danger"; note = "飲水明顯不足，請多喝水。"; }
        return new Metric("飲水達標率", waterMl + " / " + WATER_GOAL_ML + " ml（" + pct + "%）", level, status, note);
    }

    /**
     * 綜合健康分數（0~100 啟發式健康指數，非決策樹本身）。
     * 以 100 為基準，依各項判讀的 warn/danger 扣分；決策樹高/中風險加重扣分。
     */
    private void computeScoreAndAdvice(AssessmentResult r) {
        int score = 100;
        List<Metric> metrics = new ArrayList<>();
        for (Metric m : new Metric[]{r.bmi, r.bloodPressure, r.bloodSugar, r.heartRate, r.bodyTemp, r.water}) {
            if (m != null) metrics.add(m);
        }
        for (Metric m : metrics) {
            if ("danger".equals(m.status)) { score -= 15; r.recommendations.add(m.name + "：" + m.note); }
            else if ("warn".equals(m.status)) { score -= 7; r.recommendations.add(m.name + "：" + m.note); }
        }
        // 決策樹風險扣分
        if ("高".equals(r.decisionTreeRisk)) { score -= 20; r.recommendations.add("睡眠/步數/心情綜合風險為「高」，請優先改善作息與活動量。"); }
        else if ("中".equals(r.decisionTreeRisk)) { score -= 10; r.recommendations.add("睡眠/步數/心情綜合風險為「中」，建議微調生活習慣。"); }

        score = Math.max(0, Math.min(100, score));
        r.compositeScore = score;
        if (score >= 90) r.scoreGrade = "優";
        else if (score >= 75) r.scoreGrade = "良";
        else if (score >= 60) r.scoreGrade = "普通";
        else r.scoreGrade = "需注意";

        if (r.recommendations.isEmpty()) r.recommendations.add("各項指標良好，請繼續保持健康生活型態！");
    }
}
