package com.healthlog.service;

import com.healthlog.dto.ClinicResult;
import com.healthlog.dto.ClinicResult.SymptomAssessment;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 「智慧醫療診療室」服務（§11）。
 *
 * 使用者輸入症狀 → 規則式知識庫對應可能病因、建議科別、自我照護與警訊；
 * 並結合使用者「已輸入過的數據」（最新血壓/血糖/體溫）做交叉判斷，
 * 提出更貼合個人的建議。純規則，無外部 AI。
 */
@Service
public class ClinicService {

    /** 一個症狀的知識：可能病因、建議科別、自我照護、是否需警覺紅旗 */
    private record Knowledge(String[] aliases, String specialty, String[] causes, String advice, boolean redFlag) {}

    private static final Map<String, Knowledge> KB = new LinkedHashMap<>();
    static {
        KB.put("頭痛", new Knowledge(new String[]{"頭痛", "偏頭痛"}, "神經內科",
                new String[]{"睡眠不足", "壓力緊張", "血壓偏高", "咖啡因戒斷", "脫水"},
                "規律作息、補充水分、量血壓；避免螢幕過久", false));
        KB.put("頭暈", new Knowledge(new String[]{"頭暈", "暈眩", "暈"}, "神經內科 / 耳鼻喉科",
                new String[]{"血壓異常", "貧血", "內耳不平衡", "睡眠不足"},
                "起身放慢、補水；反覆發作建議就醫檢查", false));
        KB.put("心悸", new Knowledge(new String[]{"心悸", "心跳快", "心律不整", "胸悶"}, "心臟內科",
                new String[]{"咖啡因過量", "焦慮", "心律不整", "甲狀腺亢進"},
                "減少咖啡因、量血壓與心率、深呼吸放鬆", true));
        KB.put("失眠", new Knowledge(new String[]{"失眠", "睡不著", "淺眠"}, "身心科 / 家醫科",
                new String[]{"壓力焦慮", "咖啡因", "作息不規律", "藍光暴露"},
                "睡眠衛生：固定就寢、睡前遠離手機、午後不喝咖啡", false));
        KB.put("胃痛", new Knowledge(new String[]{"胃痛", "噁心", "胃食道逆流", "胃脹"}, "腸胃科",
                new String[]{"飲食不當", "壓力", "胃酸過多"},
                "清淡少量多餐、避免油炸與宵夜", false));
        KB.put("發燒", new Knowledge(new String[]{"發燒", "發熱"}, "家醫科 / 感染科",
                new String[]{"感染", "發炎"},
                "多休息補水、監測體溫；高燒不退或超過 39°C 盡速就醫", true));
        KB.put("咳嗽", new Knowledge(new String[]{"咳嗽", "喉嚨痛", "喉嚨癢"}, "耳鼻喉科 / 家醫科",
                new String[]{"上呼吸道感染", "過敏", "空氣品質差"},
                "多喝溫水、戴口罩；咳嗽超過兩週建議就醫", false));
        KB.put("腹瀉", new Knowledge(new String[]{"腹瀉", "拉肚子"}, "腸胃科",
                new String[]{"飲食/腸胃炎", "食物不潔"},
                "補充電解質與水分、清淡飲食；血便或脫水盡速就醫", false));
        KB.put("便祕", new Knowledge(new String[]{"便祕"}, "腸胃科",
                new String[]{"纖維/水分不足", "缺乏運動"},
                "增加蔬果纖維與水分、規律運動", false));
        KB.put("肩頸痠", new Knowledge(new String[]{"肩頸痠", "肩頸痛", "腰痠", "腰痛", "背痛"}, "復健科 / 骨科",
                new String[]{"姿勢不良", "久坐", "缺乏伸展"},
                "每小時起身伸展、調整桌椅高度、熱敷", false));
        KB.put("疲勞", new Knowledge(new String[]{"疲勞", "疲憊", "倦怠", "沒力氣"}, "家醫科",
                new String[]{"睡眠不足", "壓力", "營養不均", "甲狀腺/貧血"},
                "規律睡眠、均衡飲食；長期倦怠建議抽血檢查", false));
        KB.put("眼睛痠", new Knowledge(new String[]{"眼睛痠", "眼睛痛", "視力模糊"}, "眼科",
                new String[]{"用眼過度", "螢幕藍光"},
                "20-20-20 護眼原則、適度休息", false));
    }

    private final VitalSignRepository vitalRepo;

    public ClinicService(VitalSignRepository vitalRepo) {
        this.vitalRepo = vitalRepo;
    }

    public ClinicResult diagnose(List<String> symptoms) {
        ClinicResult res = new ClinicResult();
        if (symptoms == null) symptoms = new ArrayList<>();

        boolean anyRedFlag = false;
        Set<String> matchedKeys = new LinkedHashSet<>();

        for (String raw : symptoms) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();
            res.inputSymptoms.add(s);
            // 比對知識庫（含別名包含）
            String matchKey = matchKey(s);
            if (matchKey == null) continue;
            if (!matchedKeys.add(matchKey)) continue;
            Knowledge k = KB.get(matchKey);

            SymptomAssessment a = new SymptomAssessment();
            a.symptom = s;
            a.specialty = k.specialty();
            a.possibleCauses.addAll(Arrays.asList(k.causes()));
            a.advice = k.advice();
            a.redFlag = k.redFlag();
            res.assessments.add(a);
            res.recommendedSpecialties.add(k.specialty());
            if (k.advice() != null) res.selfCare.add(k.specialty() + "：" + k.advice());
            if (k.redFlag()) anyRedFlag = true;
        }

        // 結合使用者既有數據做交叉判斷
        crossReferenceData(res, matchedKeys);

        // 緊急度
        if (anyRedFlag) { res.urgency = "soon"; res.urgencyLabel = "建議盡快安排就醫評估"; }
        else if (res.assessments.isEmpty()) { res.urgency = "routine"; res.urgencyLabel = "未對應到已知症狀，請描述更具體或諮詢醫師"; }
        else { res.urgency = "routine"; res.urgencyLabel = "可先自我照護觀察，必要時預約門診"; }

        if (res.selfCare.isEmpty()) res.selfCare.add("維持規律作息、均衡飲食與適度運動。");
        return res;
    }

    /** 用最新生命徵象與症狀交叉，揪出個人化警訊 */
    private void crossReferenceData(ClinicResult res, Set<String> matchedKeys) {
        VitalSign v = vitalRepo.findFirstByUserIdOrderByRecordDateDescIdDesc(CurrentUser.id());
        if (v == null) return;

        if (v.getSystolic() != null && v.getSystolic() >= 140) {
            res.dataInsights.add("你最近一次收縮壓 " + v.getSystolic() + " mmHg 偏高。");
            if (matchedKeys.contains("頭痛") || matchedKeys.contains("頭暈") || matchedKeys.contains("心悸")) {
                res.dataInsights.add("⚠ 高血壓合併頭痛/頭暈/心悸，建議優先至『心臟內科』評估。");
                res.recommendedSpecialties.add("心臟內科");
            }
        }
        if (v.getBloodSugar() != null && v.getBloodSugar() >= 126) {
            res.dataInsights.add("你最近一次空腹血糖 " + v.getBloodSugar() + " mg/dL 偏高，建議『新陳代謝科』檢查。");
            res.recommendedSpecialties.add("新陳代謝科");
        }
        if (v.getBodyTemp() != null && v.getBodyTemp() >= 38.0) {
            res.dataInsights.add("你最近一次體溫 " + v.getBodyTemp() + "°C 已達發燒，請密切觀察。");
        }
        if (v.getHeartRate() != null && v.getHeartRate() > 100 && matchedKeys.contains("心悸")) {
            res.dataInsights.add("最近心率 " + v.getHeartRate() + " bpm 偏快，與心悸症狀一致，建議就醫。");
        }
    }

    private String matchKey(String input) {
        for (Map.Entry<String, Knowledge> e : KB.entrySet()) {
            for (String alias : e.getValue().aliases()) {
                if (input.contains(alias) || alias.contains(input)) return e.getKey();
            }
        }
        return null;
    }

    /** 提供前端可選的常見症狀清單 */
    public List<String> commonSymptoms() {
        return new ArrayList<>(List.of("頭痛", "頭暈", "心悸", "胸悶", "失眠", "胃痛", "噁心",
                "發燒", "咳嗽", "喉嚨痛", "腹瀉", "便祕", "肩頸痠", "腰痠", "疲勞", "眼睛痠"));
    }
}
