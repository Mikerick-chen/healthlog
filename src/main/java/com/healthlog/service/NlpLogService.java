package com.healthlog.service;

import com.healthlog.dto.NlpParseResult;
import com.healthlog.dto.NlpParseResult.Tag;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「NLP 語意智能日誌」服務（§1）。
 *
 * 純規則式中文關鍵字抽取（不呼叫任何外部 AI API）：
 * 把白話文（例：「今天為了趕報告喝了三杯美式，晚上有點心悸」）解析成
 * 結構化健康訊號（咖啡因過量、壓力、心率異常…），並推估可回填的數值，
 * 達成「零摩擦無痛紀錄」。
 */
@Service
public class NlpLogService {

    /** 一個關鍵字規則：類別、命中詞、健康意涵、狀態 */
    private record Rule(String category, String[] keywords, String message, String status) {}

    // 知識庫：中文關鍵字 → 健康意涵
    private static final List<Rule> RULES = List.of(
        new Rule("咖啡因", new String[]{"美式", "拿鐵", "咖啡", "卡布", "濃縮", "espresso", "coffee", "能量飲", "紅牛", "提神"},
                "攝取咖啡因，過量可能造成心悸、失眠", "warn"),
        new Rule("酒精", new String[]{"啤酒", "紅酒", "白酒", "調酒", "高粱", "威士忌", "清酒", "喝酒", "宿醉"},
                "攝取酒精，影響睡眠品質與肝臟代謝", "warn"),
        new Rule("睡眠不足", new String[]{"失眠", "睡不著", "淺眠", "熬夜", "早醒", "睡不好", "沒睡好", "通宵"},
                "睡眠品質不佳，注意休息", "danger"),
        new Rule("睡眠良好", new String[]{"睡得好", "好眠", "睡飽", "睡得香", "一覺到天亮"},
                "睡眠品質良好", "good"),
        new Rule("心率異常", new String[]{"心悸", "心跳快", "心律不整", "胸悶", "心臟亂跳"},
                "出現心率/胸悶症狀，建議量測血壓與心率", "danger"),
        new Rule("運動", new String[]{"跑步", "健身", "重訓", "游泳", "走路", "散步", "瑜珈", "騎車", "爬山", "運動", "健走"},
                "有運動，有助心肺與情緒", "good"),
        new Rule("飲水", new String[]{"喝水", "補水", "水分", "喝了水"},
                "補充水分，維持代謝", "good"),
        new Rule("壓力", new String[]{"壓力", "焦慮", "緊張", "趕報告", "加班", "崩潰", "煩", "心累", "爆肝", "deadline"},
                "處於高壓狀態，注意情緒與睡眠", "warn"),
        new Rule("正向情緒", new String[]{"開心", "愉快", "放鬆", "平靜", "充實", "幸福", "舒服"},
                "情緒正向", "good"),
        new Rule("症狀", new String[]{"頭痛", "偏頭痛", "胃痛", "噁心", "頭暈", "暈", "發燒", "咳嗽", "喉嚨痛",
                "拉肚子", "腹瀉", "便祕", "肩頸痠", "腰痠", "眼睛痠", "疲憊", "疲勞", "倦怠"},
                "出現身體不適症狀，可至智慧診療室評估", "danger"),
        new Rule("飲食過量", new String[]{"吃太多", "大餐", "油炸", "甜食", "宵夜", "暴飲暴食", "重口味"},
                "飲食偏負擔，注意份量與油鹽糖", "warn")
    );

    // 症狀詞（供診療室帶入）
    private static final String[] SYMPTOM_WORDS = {
        "頭痛", "偏頭痛", "胃痛", "噁心", "頭暈", "發燒", "咳嗽", "喉嚨痛", "腹瀉", "便祕",
        "心悸", "胸悶", "失眠", "肩頸痠", "腰痠", "疲勞", "疲憊"
    };

    public NlpParseResult parse(String text) {
        NlpParseResult res = new NlpParseResult();
        res.originalText = text == null ? "" : text;
        if (text == null || text.isBlank()) {
            res.summary = "請輸入內容";
            return res;
        }

        Map<String, Integer> catCount = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        // 1) 關鍵字比對
        for (Rule rule : RULES) {
            for (String kw : rule.keywords()) {
                if (text.contains(kw)) {
                    String dedupe = rule.category() + ":" + kw;
                    if (seen.add(dedupe)) {
                        res.tags.add(new Tag(rule.category(), kw, rule.message(), rule.status()));
                        catCount.merge(rule.category(), 1, Integer::sum);
                    }
                }
            }
        }
        catCount.forEach((c, n) -> res.categoryChart.add(new NlpParseResult.CategoryCount(c, n)));

        // 2) 數值抽取
        res.extracted.caffeineCups = numberBefore(text, new String[]{"杯"});
        res.extracted.sleepHours = doubleBeforeAny(text, new String[]{"小時", "hr", "個鐘頭"}, new String[]{"睡"});
        res.extracted.steps = numberBefore(text, new String[]{"步"});
        res.extracted.waterMl = waterMl(text);

        // 3) 症狀
        for (String sym : SYMPTOM_WORDS) if (text.contains(sym)) res.extracted.symptoms.add(sym);

        // 4) 推估心情（依正負向關鍵字與症狀加減）
        res.extracted.moodGuess = guessMood(res);

        // 5) 一句話總結
        res.summary = buildSummary(res);
        return res;
    }

    /** 抽取「數字＋單位」的整數，例如「三杯」「8000步」 */
    private Integer numberBefore(String text, String[] units) {
        for (String unit : units) {
            Matcher m = Pattern.compile("([0-9]+|[一二兩三四五六七八九十]+)\\s*" + Pattern.quote(unit)).matcher(text);
            if (m.find()) {
                Integer v = parseCnNumber(m.group(1));
                if (v != null) return v;
            }
        }
        return null;
    }

    /** 抽取睡眠時數等小數，需附近出現語境詞（例如「睡」） */
    private Double doubleBeforeAny(String text, String[] units, String[] contextWords) {
        boolean hasContext = false;
        for (String c : contextWords) if (text.contains(c)) hasContext = true;
        if (!hasContext) return null;
        for (String unit : units) {
            Matcher m = Pattern.compile("([0-9]+(?:\\.[0-9]+)?|[一二兩三四五六七八九十]+)\\s*" + Pattern.quote(unit)).matcher(text);
            if (m.find()) {
                String g = m.group(1);
                if (g.matches("[0-9.]+")) return Double.valueOf(g);
                Integer v = parseCnNumber(g);
                if (v != null) return v.doubleValue();
            }
        }
        return null;
    }

    /** 喝水量：支援「2000cc / 2公升 / 兩杯水」 */
    private Integer waterMl(String text) {
        Matcher ml = Pattern.compile("([0-9]+)\\s*(cc|ml|毫升)").matcher(text);
        if (ml.find()) return Integer.valueOf(ml.group(1));
        Matcher l = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(公升|L|l|升)").matcher(text);
        if (l.find()) return (int) Math.round(Double.parseDouble(l.group(1)) * 1000);
        if (text.contains("水")) {
            Integer cups = numberBefore(text, new String[]{"杯"});
            if (cups != null && (text.contains("水") )) return cups * 250; // 一杯約 250ml
        }
        return null;
    }

    /** 中文/阿拉伯數字轉整數（支援 一~十、兩、含十位如十二/二十） */
    private Integer parseCnNumber(String s) {
        if (s == null) return null;
        if (s.matches("[0-9]+")) return Integer.valueOf(s);
        Map<Character, Integer> d = Map.of('一', 1, '二', 2, '兩', 2, '三', 3, '四', 4,
                '五', 5, '六', 6, '七', 7, '八', 8, '九', 9);
        if (s.equals("十")) return 10;
        if (s.contains("十")) {
            int idx = s.indexOf('十');
            int tens = idx == 0 ? 1 : d.getOrDefault(s.charAt(idx - 1), 1);
            int ones = idx == s.length() - 1 ? 0 : d.getOrDefault(s.charAt(idx + 1), 0);
            return tens * 10 + ones;
        }
        if (s.length() == 1) return d.get(s.charAt(0));
        return null;
    }

    /** 依正負向標籤推估心情 1-10 */
    private Integer guessMood(NlpParseResult res) {
        int score = 6; // 中性偏上
        for (Tag t : res.tags) {
            switch (t.status) {
                case "good" -> score += 1;
                case "warn" -> score -= 1;
                case "danger" -> score -= 2;
            }
        }
        return Math.max(1, Math.min(10, score));
    }

    private String buildSummary(NlpParseResult res) {
        if (res.tags.isEmpty()) return "未偵測到明顯健康關鍵字，已保留原文。";
        StringBuilder sb = new StringBuilder("偵測到 ");
        sb.append(res.categoryChart.size()).append(" 類訊號：");
        sb.append(String.join("、", res.categoryChart.stream().map(c -> c.category).toList()));
        if (!res.extracted.symptoms.isEmpty()) {
            sb.append("；症狀「").append(String.join("、", res.extracted.symptoms)).append("」建議至智慧診療室評估");
        }
        sb.append("。推估心情 ").append(res.extracted.moodGuess).append("/10。");
        return sb.toString();
    }
}
