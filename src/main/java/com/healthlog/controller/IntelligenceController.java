package com.healthlog.controller;

import com.healthlog.dto.*;
import com.healthlog.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 智能引擎 REST 控制器：NLP 語意日誌、洞察分析（基準線/ROI/教練）、智慧診療室、環境關聯。
 * 全部為自寫演算法，未呼叫任何外部 AI API。
 */
@RestController
public class IntelligenceController {

    private final NlpLogService nlp;
    private final BaselineService baseline;
    private final RoiService roi;
    private final CoachService coach;
    private final ClinicService clinic;
    private final EnvironmentService environment;

    public IntelligenceController(NlpLogService nlp, BaselineService baseline, RoiService roi,
                                  CoachService coach, ClinicService clinic, EnvironmentService environment) {
        this.nlp = nlp;
        this.baseline = baseline;
        this.roi = roi;
        this.coach = coach;
        this.clinic = clinic;
        this.environment = environment;
    }

    // ---- #1 NLP 語意日誌 ----
    @PostMapping("/nlp/parse")
    public NlpParseResult parse(@RequestBody Map<String, String> body) {
        return nlp.parse(body.get("text"));
    }

    // ---- #3 動態專屬基準線 ----
    @GetMapping("/insights/baseline")
    public BaselineResult baseline() { return baseline.compute(); }

    // ---- #2 健康 ROI ----
    @GetMapping("/insights/roi")
    public RoiResult roi() { return roi.compute(); }

    // ---- #4 自適應情緒教練 ----
    @GetMapping("/insights/coach")
    public CoachMessage coach() { return coach.coach(); }

    // ---- #3b 跨維度環境關聯 ----
    @GetMapping("/environment")
    public EnvironmentInfo environment(@RequestParam(required = false) Double lat,
                                       @RequestParam(required = false) Double lon) {
        return environment.today(lat, lon);
    }

    // ---- #11 智慧診療室 ----
    @GetMapping("/clinic/symptoms")
    public List<String> commonSymptoms() { return clinic.commonSymptoms(); }

    @PostMapping("/clinic/diagnose")
    @SuppressWarnings("unchecked")
    public ClinicResult diagnose(@RequestBody Map<String, Object> body) {
        List<String> symptoms = new ArrayList<>();
        Object raw = body.get("symptoms");
        if (raw instanceof List<?> list) for (Object o : list) symptoms.add(String.valueOf(o));
        // 若帶自由文字，先用 NLP 抽出症狀一併納入
        Object text = body.get("text");
        if (text != null && !text.toString().isBlank()) {
            NlpParseResult parsed = nlp.parse(text.toString());
            symptoms.addAll(parsed.extracted.symptoms);
        }
        return clinic.diagnose(symptoms);
    }
}
