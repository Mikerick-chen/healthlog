package com.healthlog.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 智慧診療室評估結果（§11）。
 * 依輸入症狀＋使用者既有數據，給出可能病因、建議科別、自我照護與警訊。
 * 僅供健康管理參考，非醫療診斷。
 */
public class ClinicResult {

    public List<String> inputSymptoms = new ArrayList<>();
    public List<SymptomAssessment> assessments = new ArrayList<>();
    public Set<String> recommendedSpecialties = new LinkedHashSet<>();
    public List<String> dataInsights = new ArrayList<>();   // 結合使用者數據的發現
    public List<String> selfCare = new ArrayList<>();
    public String urgency;        // routine / soon / urgent
    public String urgencyLabel;
    public String disclaimer = "本評估為健康管理參考，非醫療診斷，無法取代專業醫師。症狀持續或惡化請盡速就醫。";

    public static class SymptomAssessment {
        public String symptom;
        public List<String> possibleCauses = new ArrayList<>();
        public String specialty;
        public String advice;
        public boolean redFlag;
    }
}
