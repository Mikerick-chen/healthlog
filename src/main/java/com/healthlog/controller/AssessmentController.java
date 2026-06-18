package com.healthlog.controller;

import com.healthlog.dto.AssessmentResult;
import com.healthlog.service.HealthAssessmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 綜合健康評估 REST 控制器（§4 更有效評估） */
@RestController
@RequestMapping("/assessment")
public class AssessmentController {

    private final HealthAssessmentService service;

    public AssessmentController(HealthAssessmentService service) {
        this.service = service;
    }

    /** GET /assessment — 對最新資料做綜合評估（BMI/血壓/血糖/心率/體溫/水分 + 綜合分數 + 建議） */
    @GetMapping
    public AssessmentResult latest() {
        return service.assessLatest();
    }
}
