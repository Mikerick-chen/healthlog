package com.healthlog.service;

import com.healthlog.dto.RiskResult;
import com.healthlog.entity.BodyMetric;
import com.healthlog.entity.DiaryEntry;
import com.healthlog.entity.HealthLog;
import com.healthlog.entity.VitalSign;
import com.healthlog.entity.User;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.repository.DiaryEntryRepository;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.repository.UserRepository;
import com.healthlog.repository.VitalSignRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 種子資料服務（CommandLineRunner）。
 *
 * 啟動流程：
 *  1. 依 §6.1 三組分布，「刻意安排規律」生成 90 天資料（🔒 非純隨機，固定亂數種子可重現）。
 *  2. 以三組設計標籤計算資訊增益 → 導出決策樹門檻 T1/T2/T3 並套用（§6.3，每次啟動都會跑一次並 log）。
 *  3. 若資料庫為空，寫入 90 筆，並用決策樹回填 risk_level；同時輸出 health_logs_seed.sql 交付檔。
 */
@Component
@Order(1)
public class SeedDataService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);
    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final long SEED = 42L; // 固定種子 → 規律可重現、可驗證

    private final HealthLogRepository repository;
    private final InformationGainService infoGain;
    private final DecisionTreeService decisionTree;
    private final BodyMetricRepository bodyRepo;
    private final VitalSignRepository vitalRepo;
    private final DiaryEntryRepository diaryRepo;
    private final UserRepository userRepo;

    /** 種子資料歸屬的示範使用者 ID（建立後填入） */
    private Long demoUserId;

    public SeedDataService(HealthLogRepository repository,
                           InformationGainService infoGain,
                           DecisionTreeService decisionTree,
                           BodyMetricRepository bodyRepo,
                           VitalSignRepository vitalRepo,
                           DiaryEntryRepository diaryRepo,
                           UserRepository userRepo) {
        this.repository = repository;
        this.infoGain = infoGain;
        this.decisionTree = decisionTree;
        this.bodyRepo = bodyRepo;
        this.vitalRepo = vitalRepo;
        this.diaryRepo = diaryRepo;
        this.userRepo = userRepo;
    }

    /** 內部用：一筆種子（含設計組別標籤） */
    private record Seed(LocalDate date, double sleep, int steps, int mood, String group) {}

    @Override
    public void run(String... args) {
        // ---- 0. 建立示範使用者（種子資料歸屬於它；真實新使用者自己的資料為空，§9）----
        User demo = userRepo.findByName("示範帳號").orElseGet(() -> {
            User u = new User("示範帳號");
            u.setLocation("台北");
            u.setLatitude(25.04);
            u.setLongitude(121.56);
            return userRepo.save(u);
        });
        demoUserId = demo.getId();

        // ---- 1. 生成 90 天規律資料 ----
        List<Seed> seeds = generateSeeds();

        // ---- 2. 資訊增益實測 → 導出並套用門檻（每次啟動都會跑，滿足「至少跑過一次並記錄」）----
        List<InformationGainService.Sample> samples = seeds.stream()
                .map(s -> new InformationGainService.Sample(s.sleep(), s.steps(), s.mood(), s.group()))
                .toList();
        InformationGainService.Thresholds t = infoGain.deriveThresholds(samples);
        decisionTree.applyThresholds(t.sleepThreshold(), t.stepsThreshold(), t.moodThreshold());
        // A4：以設計組別為標籤，校準各葉節點信心值
        decisionTree.calibrateConfidence(samples);

        // ---- 3. 寫入 DB（僅當示範使用者尚無資料）+ 回填 risk_level ----
        if (repository.countByUserId(demoUserId) == 0) {
            int high = 0, mid = 0, low = 0;
            for (Seed s : seeds) {
                HealthLog e = new HealthLog(s.date(), s.sleep(), s.steps(), s.mood());
                e.setUserId(demoUserId);
                e = repository.save(e);
                RiskResult r = decisionTree.classify(e);
                e.setRiskLevel(r.getRiskLevel());
                repository.save(e);
                switch (r.getRiskLevel()) {
                    case "高" -> high++;
                    case "中" -> mid++;
                    default -> low++;
                }
            }
            log.info("已寫入 90 筆種子資料。決策樹判定分布：高={} 中={} 低={}（對應 §6.1 設計：高~25 中~40 低~25）",
                    high, mid, low);
        } else {
            log.info("資料庫已有 {} 筆資料，略過種子寫入（門檻仍已重新計算套用）。", repository.count());
        }

        // ---- 種子：身體數據 / 生命徵象 / 日記（與健康日誌同步，依組別關聯）----
        seedExtras(seeds);

        // ---- 交付：輸出 SQL INSERT 檔（risk_level 留空，因其為計算結果）----
        writeSeedSqlFile(seeds);
    }

    /**
     * 種子身體數據、生命徵象、日記。
     * 刻意讓「高風險組」對應較差的指標（較高血壓/血糖、較少喝水），低風險組相反，
     * 使綜合評估與相關性分析有可觀察的規律（§4）。
     */
    private void seedExtras(List<Seed> seeds) {
        Random rnd = new Random(SEED + 1);

        if (bodyRepo.countByUserId(demoUserId) == 0) {
            double weight = 70.0; // 體重以隨機漫步緩慢變化，呈現趨勢
            for (Seed s : seeds) {
                BodyMetric b = new BodyMetric();
                b.setUserId(demoUserId);
                b.setRecordDate(s.date());
                b.setHeightCm(170.0);
                weight += (rnd.nextDouble() - 0.5) * 0.6;       // ±0.3kg/日
                b.setWeightKg(round1(Math.max(60, Math.min(80, weight))));
                int water = switch (s.group()) {                // 喝水量依組別
                    case "高" -> 700 + rnd.nextInt(600);        // 700–1300
                    case "低" -> 2000 + rnd.nextInt(800);       // 2000–2800
                    default -> 1400 + rnd.nextInt(600);          // 1400–2000
                };
                b.setWaterMl(water);
                bodyRepo.save(b);
            }
            log.info("已寫入 90 筆身體數據種子。");
        }

        if (vitalRepo.countByUserId(demoUserId) == 0) {
            for (Seed s : seeds) {
                VitalSign v = new VitalSign();
                v.setUserId(demoUserId);
                v.setRecordDate(s.date());
                switch (s.group()) {
                    case "高" -> {
                        v.setSystolic(140 + rnd.nextInt(25));   // 偏高
                        v.setDiastolic(90 + rnd.nextInt(15));
                        v.setHeartRate(85 + rnd.nextInt(25));
                        v.setBodyTemp(round1(36.5 + rnd.nextDouble()));
                        v.setBloodSugar(120 + rnd.nextInt(60));
                    }
                    case "低" -> {
                        v.setSystolic(108 + rnd.nextInt(10));   // 正常
                        v.setDiastolic(68 + rnd.nextInt(10));
                        v.setHeartRate(62 + rnd.nextInt(15));
                        v.setBodyTemp(round1(36.3 + rnd.nextDouble() * 0.6));
                        v.setBloodSugar(85 + rnd.nextInt(15));
                    }
                    default -> {
                        v.setSystolic(120 + rnd.nextInt(12));
                        v.setDiastolic(78 + rnd.nextInt(10));
                        v.setHeartRate(72 + rnd.nextInt(15));
                        v.setBodyTemp(round1(36.4 + rnd.nextDouble() * 0.7));
                        v.setBloodSugar(95 + rnd.nextInt(25));
                    }
                }
                v.setMeasureContext("空腹");
                vitalRepo.save(v);
            }
            log.info("已寫入 90 筆生命徵象種子。");
        }

        if (diaryRepo.countByUserId(demoUserId) == 0) {
            // 取最近 8 天寫日記示例
            int n = seeds.size();
            String[] moods = {"開心", "平靜", "疲憊", "焦慮", "充實"};
            for (int i = Math.max(0, n - 8); i < n; i++) {
                Seed s = seeds.get(i);
                DiaryEntry d = new DiaryEntry();
                d.setUserId(demoUserId);
                d.setEntryDate(s.date());
                d.setTitle("今日健康紀錄");
                d.setContent(switch (s.group()) {
                    case "高" -> "今天睡得不好，提不起勁，步數也很少，心情有點低落。";
                    case "低" -> "今天精神很好，運動量充足，睡眠品質佳，整天都很有活力。";
                    default -> "今天狀態普通，工作有點忙，提醒自己多喝水、早點休息。";
                });
                d.setMoodTag(moods[rnd.nextInt(moods.length)]);
                d.setSymptomTags(s.group().equals("高") ? "疲勞,失眠" : "");
                diaryRepo.save(d);
            }
            log.info("已寫入日記種子。");
        }
    }

    /**
     * 依 §6.1 生成 90 天三組資料。
     * 高風險組數值「壓更極端」（依題目追問加強規律），確保與正常組明顯落差。
     */
    private List<Seed> generateSeeds() {
        Random rnd = new Random(SEED);

        // 組別清單：25 高 + 40 中 + 25 低，洗牌後分配到 90 天（讓趨勢圖有起伏，但筆數固定）
        List<String> groups = new ArrayList<>();
        for (int i = 0; i < 25; i++) groups.add("高");
        for (int i = 0; i < 40; i++) groups.add("中");
        for (int i = 0; i < 25; i++) groups.add("低");
        Collections.shuffle(groups, rnd);

        List<Seed> seeds = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(89);
        for (int i = 0; i < 90; i++) {
            LocalDate date = start.plusDays(i);
            String g = groups.get(i);
            double sleep;
            int steps, mood;
            switch (g) {
                case "高" -> { // 極端：睡眠 3.5–5.0、步數 800–3000、心情 1–3
                    sleep = round1(3.5 + rnd.nextDouble() * 1.5);
                    steps = 800 + rnd.nextInt(2201);
                    mood = 1 + rnd.nextInt(3);
                }
                case "低" -> { // 睡眠 7.0–9.0、步數 6500–11000、心情 7–9
                    sleep = round1(7.0 + rnd.nextDouble() * 2.0);
                    steps = 6500 + rnd.nextInt(4501);
                    mood = 7 + rnd.nextInt(3);
                }
                default -> { // 中：睡眠 5.5–7.0、步數 3800–5800、心情 4–6
                    sleep = round1(5.5 + rnd.nextDouble() * 1.5);
                    steps = 3800 + rnd.nextInt(2001);
                    mood = 4 + rnd.nextInt(3);
                }
            }
            seeds.add(new Seed(date, sleep, steps, mood, g));
        }
        return seeds;
    }

    /** 輸出符合 health_logs 結構的 SQL INSERT（risk_level 留空，為計算結果） */
    private void writeSeedSqlFile(List<Seed> seeds) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- 智慧健康日誌 90 天種子資料（依 §6.1 三組規律，risk_level 留空＝由決策樹計算）\n");
        sb.append("-- 產生 prompt 摘要：90 天健康日誌，高風險組(睡眠3.5-5.0/步數800-3000/心情1-3)極端壓低，\n");
        sb.append("--                  普通組(5.5-7.0/3800-5800/4-6)，低風險組(7.0-9.0/6500-11000/7-9)；固定種子可重現。\n");
        for (Seed s : seeds) {
            sb.append(String.format(
                    "INSERT INTO health_logs (log_date, sleep_hours, steps, mood_score, risk_level) VALUES ('%s', %.1f, %d, %d, NULL); -- 設計組別:%s%n",
                    s.date().format(DF), s.sleep(), s.steps(), s.mood(), s.group()));
        }
        try {
            Path out = Path.of("health_logs_seed.sql");
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
            log.info("已輸出種子資料 SQL 交付檔：{}", out.toAbsolutePath());
        } catch (IOException ex) {
            log.warn("輸出 health_logs_seed.sql 失敗：{}", ex.getMessage());
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
