package com.healthlog.service;

import com.healthlog.dto.ClinicResult;
import com.healthlog.dto.ClinicResult.SymptomAssessment;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.VitalSignRepository;
import com.healthlog.security.CurrentUser;
import com.healthlog.service.KnowledgeBaseService.Concept;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 「智慧醫療診療室」服務（§11）。
 *
 * 使用者輸入症狀 → 知識圖譜對應可能病因、建議科別、自我照護與警訊；
 * 並結合使用者「已輸入過的數據」（最新血壓/血糖/體溫）做交叉判斷。純規則，無外部 AI。
 *
 * B6：症狀知識與 {@link NlpLogService} 共用同一份 {@link KnowledgeBaseService}（單一真相來源）。
 */
@Service
public class ClinicService {

    private final VitalSignRepository vitalRepo;
    private final KnowledgeBaseService knowledge;

    public ClinicService(VitalSignRepository vitalRepo, KnowledgeBaseService knowledge) {
        this.vitalRepo = vitalRepo;
        this.knowledge = knowledge;
    }

    public ClinicResult diagnose(List<String> symptoms) {
        ClinicResult res = new ClinicResult();
        if (symptoms == null) symptoms = new ArrayList<>();

        boolean anyRedFlag = false;
        Set<String> matchedKeys = new LinkedHashSet<>();
        Map<String, Concept> kb = knowledge.symptomByName();

        for (String raw : symptoms) {
            if (raw == null || raw.isBlank()) continue;
            String s = raw.trim();
            res.inputSymptoms.add(s);
            String key = matchKey(s, kb);          // 對應到知識圖譜的正規症狀名
            if (key == null) continue;
            if (!matchedKeys.add(key)) continue;
            Concept c = kb.get(key);

            SymptomAssessment a = new SymptomAssessment();
            a.symptom = s;
            a.specialty = c.specialty();
            a.possibleCauses.addAll(c.causes());
            a.advice = c.advice();
            a.redFlag = c.redFlag();
            res.assessments.add(a);
            if (c.specialty() != null) res.recommendedSpecialties.add(c.specialty());
            if (c.advice() != null) res.selfCare.add(c.specialty() + "：" + c.advice());
            if (c.redFlag()) anyRedFlag = true;
        }

        crossReferenceData(res, matchedKeys);

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

    /** 把輸入文字對應到知識圖譜中的正規症狀名（含別名包含比對） */
    private String matchKey(String input, Map<String, Concept> kb) {
        for (Map.Entry<String, Concept> e : kb.entrySet()) {
            for (String alias : e.getValue().keywords()) {
                if (input.contains(alias) || alias.contains(input)) return e.getKey();
            }
        }
        return null;
    }

    /** 提供前端可選的常見症狀清單（取自知識圖譜，B6） */
    public List<String> commonSymptoms() {
        return knowledge.commonSymptoms();
    }
}
