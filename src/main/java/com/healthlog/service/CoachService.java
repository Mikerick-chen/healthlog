package com.healthlog.service;

import com.healthlog.dto.CoachMessage;
import com.healthlog.entity.HealthLog;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 「自適應情緒」AI 擬真教練（§4）。
 *
 * 捨棄罐頭回覆：依使用者近 3 天的生理數據判斷當下狀態，
 * 自動切換語氣——高壓疲憊→溫柔鼓勵；鬆懈→嚴格督導；進步→熱情慶祝；穩定→陪伴，
 * 提供具「人味」的情緒價值與專屬建議（純規則，無外部 AI）。
 */
@Service
public class CoachService {

    private final HealthLogRepository healthRepo;
    private final DecisionTreeService decisionTree;

    public CoachService(HealthLogRepository healthRepo, DecisionTreeService decisionTree) {
        this.healthRepo = healthRepo;
        this.decisionTree = decisionTree;
    }

    public CoachMessage coach() {
        Long uid = CurrentUser.id();
        List<HealthLog> all = healthRepo.findByUserIdOrderByLogDateDesc(uid);
        CoachMessage m = new CoachMessage();

        if (all.isEmpty()) {
            m.tone = "steady"; m.toneLabel = "陪伴"; m.emoji = "🌱";
            m.title = "歡迎開始你的健康旅程";
            m.message = "還沒有任何紀錄喔。先從今天的睡眠、步數、心情記一筆，我就能開始陪你一起看數據、給你專屬的建議。";
            m.stateReason = "尚無資料";
            m.suggestions.add("到「健康日誌」記錄今天的三項數據");
            return m;
        }

        // 近 3 天統計
        List<HealthLog> recent = all.subList(0, Math.min(3, all.size()));
        double avgSleep = recent.stream().mapToDouble(HealthLog::getSleepHours).average().orElse(0);
        double avgSteps = recent.stream().mapToInt(HealthLog::getSteps).average().orElse(0);
        double avgMood = recent.stream().mapToInt(HealthLog::getMoodScore).average().orElse(0);
        long badSleepDays = recent.stream().filter(h -> h.getSleepHours() < 6.0).count();
        long lowMoodDays = recent.stream().filter(h -> h.getMoodScore() <= 4).count();
        long lowStepDays = recent.stream().filter(h -> h.getSteps() < 4000).count();

        HealthLog latest = all.get(0);
        String risk = decisionTree.classify(latest).getRiskLevel();

        // 狀態判斷 → 語氣
        if (badSleepDays >= 2 || lowMoodDays >= 2) {
            // 高壓疲憊 → 溫柔鼓勵
            m.tone = "gentle"; m.toneLabel = "溫柔鼓勵"; m.emoji = "🤗";
            m.title = "辛苦了，先照顧好自己";
            m.message = String.format(
                "我注意到你最近 %d 天睡眠偏少（平均 %.1f 小時）、情緒也比較低。這段日子一定不好受，先別急著要求自己什麼都做到。"
                + "今晚就早一點放下手機，給自己一個好好休息的權利，其他的我們明天再慢慢來。",
                Math.max(badSleepDays, lowMoodDays), avgSleep);
            m.stateReason = String.format("近3天睡眠不足 %d 天、低落 %d 天 → 判定高壓疲憊", badSleepDays, lowMoodDays);
            m.suggestions.add("今晚提前 30 分鐘就寢，睡前避免咖啡因");
            m.suggestions.add("做 5 分鐘深呼吸或伸展，放鬆神經");
            m.suggestions.add("把待辦事項寫下來，給大腦放假");
        } else if (avgSleep >= 7 && lowStepDays >= 2) {
            // 睡得夠卻活動量低 → 嚴格督導
            m.tone = "strict"; m.toneLabel = "嚴格督導"; m.emoji = "💪";
            m.title = "睡飽了，該動起來了！";
            m.message = String.format(
                "你最近睡眠很充足（平均 %.1f 小時），但步數只有平均 %.0f 步，明顯偏低。身體狀態夠好就別浪費！"
                + "今天請務必出門走滿 6000 步，別再找藉口——你做得到，我盯著你。",
                avgSleep, avgSteps);
            m.stateReason = String.format("睡眠充足但近3天有 %d 天步數<4000 → 判定鬆懈", lowStepDays);
            m.suggestions.add("午休或下班走 20 分鐘，補滿 6000 步");
            m.suggestions.add("把通勤改成走路或提前一站下車");
        } else if ("低".equals(risk) && avgMood >= 6) {
            // 狀態好 → 熱情慶祝
            m.tone = "celebrate"; m.toneLabel = "熱情慶祝"; m.emoji = "🎉";
            m.title = "太棒了，繼續保持！";
            m.message = String.format(
                "你最近狀態超好——平均睡眠 %.1f 小時、步數 %.0f 步、心情 %.1f 分，決策樹判定為低風險。"
                + "這就是你努力的成果，為自己鼓掌！維持這個節奏，身體會一直回饋你。",
                avgSleep, avgSteps, avgMood);
            m.stateReason = "最新風險=低且心情佳 → 判定狀態良好";
            m.suggestions.add("維持目前作息，把它變成習慣");
            m.suggestions.add("挑戰連續低風險天數紀錄");
        } else {
            // 穩定陪伴
            m.tone = "steady"; m.toneLabel = "穩定陪伴"; m.emoji = "🙂";
            m.title = "穩穩的，繼續走";
            m.message = String.format(
                "目前整體還算平穩（平均睡眠 %.1f 小時、步數 %.0f 步、心情 %.1f 分）。"
                + "沒有大問題，但還有一點進步空間。挑一件最容易的小事開始，今天就會比昨天更好一點。",
                avgSleep, avgSteps, avgMood);
            m.stateReason = "數據無明顯異常 → 穩定狀態";
            m.suggestions.add("今天多喝一杯水、多走一段路");
            m.suggestions.add("睡前回顧一件今天做得好的事");
        }
        return m;
    }
}
