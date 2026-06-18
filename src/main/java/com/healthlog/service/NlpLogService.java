package com.healthlog.service;

import com.healthlog.dto.NlpParseResult;
import com.healthlog.dto.NlpParseResult.Tag;
import com.healthlog.service.KnowledgeBaseService.Concept;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 「NLP 語意智能日誌」服務（§1）。
 *
 * 純規則式中文關鍵字抽取（不呼叫任何外部 AI API）：
 * 把白話文（例：「今天為了趕報告喝了三杯美式，晚上有點心悸」）解析成
 * 結構化健康訊號（咖啡因過量、壓力、心率異常…），並推估可回填的數值。
 *
 * B5/B6：關鍵字知識庫已外部化並與診療室共用，改由 {@link KnowledgeBaseService} 提供。
 */
@Service
public class NlpLogService {

    private final KnowledgeBaseService knowledge;

    public NlpLogService(KnowledgeBaseService knowledge) {
        this.knowledge = knowledge;
    }

    public NlpParseResult parse(String text) {
        NlpParseResult res = new NlpParseResult();
        res.originalText = text == null ? "" : text;
        if (text == null || text.isBlank()) {
            res.summary = "請輸入內容";
            return res;
        }

        Map<String, Integer> catCount = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        // 1) 關鍵字比對（B1：略過被否定詞修飾的命中，例如「今天沒有頭痛」不算頭痛）
        //    知識來源為外部化的健康知識圖譜（B5/B6）
        for (Concept c : knowledge.concepts()) {
            for (String kw : c.keywords()) {
                if (hasSignal(text, kw)) {
                    String dedupe = c.category() + ":" + kw;
                    if (seen.add(dedupe)) {
                        res.tags.add(new Tag(c.category(), kw, c.message(), c.status()));
                        catCount.merge(c.category(), 1, Integer::sum);
                    }
                }
            }
        }

        // 2) 數值抽取
        res.extracted.sleepHours = doubleBeforeAny(text, new String[]{"小時", "hr", "個鐘頭"}, new String[]{"睡"});
        res.extracted.steps = numberBefore(text, new String[]{"步"});
        res.extracted.waterMl = waterMl(text);

        // 2b) 咖啡因劑量量化（B2）：唯有偵測到咖啡因關鍵字才計杯數，避免把「一杯水」當咖啡因
        boolean hasCaffeine = res.tags.stream().anyMatch(t -> t.category.equals("咖啡因"));
        if (hasCaffeine) {
            Integer cups = numberBefore(text, new String[]{"杯"});
            if (cups == null) cups = 1; // 有提到咖啡但沒寫數量，保守估 1 杯
            res.extracted.caffeineCups = cups;
            res.extracted.caffeineMg = cups * 95; // 每杯約 95mg
            if (res.extracted.caffeineMg >= 400) { // 成人每日上限約 400mg
                res.tags.add(new Tag("咖啡因", "過量",
                        "估算攝取 " + res.extracted.caffeineMg + "mg，已超過每日建議上限 400mg，易致心悸/失眠", "danger"));
                catCount.merge("咖啡因", 1, Integer::sum);
            }
        }
        catCount.forEach((c, n) -> res.categoryChart.add(new NlpParseResult.CategoryCount(c, n)));

        // 3) 症狀（同樣套用否定過濾，B1）；任一別名命中即記錄該症狀正規名稱（B6）
        for (Concept c : knowledge.symptomConcepts()) {
            for (String kw : c.keywords()) {
                if (hasSignal(text, kw)) {
                    if (!res.extracted.symptoms.contains(c.symptomName())) res.extracted.symptoms.add(c.symptomName());
                    break;
                }
            }
        }

        // 4) 推估心情（依正負向關鍵字與症狀加減；B2 納入語氣強度）
        res.extracted.moodGuess = guessMood(res, text);

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

    // ---- B1：否定詞（命中關鍵字前若出現這些詞則視為「沒有發生」）----
    private static final String[] NEGATIONS = {"沒有", "沒", "不會", "不再", "未曾", "未", "無", "別", "免得", "不"};
    // ---- B2：語氣強度詞 ----
    private static final String[] STRONG = {"非常", "超", "很", "好", "劇烈", "嚴重", "狂", "爆", "極"};
    private static final String[] MILD = {"有點", "稍微", "輕微", "一點", "些許", "略"};

    /**
     * B1：判斷關鍵字是否為「真實訊號」——存在至少一個未被否定詞修飾的出現位置。
     * 例：「今天沒有頭痛」→ 頭痛被「沒有」否定 → 回傳 false。
     */
    private boolean hasSignal(String text, String kw) {
        int from = 0;
        while (true) {
            int idx = text.indexOf(kw, from);
            if (idx < 0) return false;
            String prefix = text.substring(Math.max(0, idx - 4), idx); // 往前看 4 字的否定窗口
            boolean negated = false;
            for (String neg : NEGATIONS) {
                if (prefix.contains(neg)) { negated = true; break; }
            }
            if (!negated) return true; // 找到一個沒被否定的出現
            from = idx + kw.length();
        }
    }

    private boolean containsAny(String text, String[] words) {
        for (String w : words) if (text.contains(w)) return true;
        return false;
    }

    /** 依正負向標籤推估心情 1-10；B2：再依語氣強度微調 */
    private Integer guessMood(NlpParseResult res, String text) {
        int score = 6; // 中性偏上
        for (Tag t : res.tags) {
            switch (t.status) {
                case "good" -> score += 1;
                case "warn" -> score -= 1;
                case "danger" -> score -= 2;
            }
        }
        // 語氣強度：有負向訊號又用了強烈語氣 → 再扣；用了輕微語氣 → 緩和
        boolean hasNegative = res.tags.stream().anyMatch(t -> !t.status.equals("good"));
        if (hasNegative && containsAny(text, STRONG)) score -= 1;
        if (hasNegative && containsAny(text, MILD)) score += 1;
        return Math.max(1, Math.min(10, score));
    }

    private String buildSummary(NlpParseResult res) {
        if (res.tags.isEmpty()) return "未偵測到明顯健康關鍵字，已保留原文。";
        StringBuilder sb = new StringBuilder("偵測到 ");
        sb.append(res.categoryChart.size()).append(" 類訊號：");
        sb.append(String.join("、", res.categoryChart.stream().map(c -> c.category).toList()));
        if (res.extracted.caffeineMg != null) {
            sb.append("；咖啡因約 ").append(res.extracted.caffeineMg).append("mg");
            if (res.extracted.caffeineMg >= 400) sb.append("（已過量）");
        }
        if (!res.extracted.symptoms.isEmpty()) {
            sb.append("；症狀「").append(String.join("、", res.extracted.symptoms)).append("」建議至智慧診療室評估");
        }
        sb.append("。推估心情 ").append(res.extracted.moodGuess).append("/10。");
        return sb.toString();
    }
}
