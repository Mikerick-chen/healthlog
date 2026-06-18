package com.healthlog.controller;

import com.healthlog.dto.AnalysisResult;
import com.healthlog.dto.HealthLogDto;
import com.healthlog.dto.HealthLogRequest;
import com.healthlog.dto.RiskResult;
import com.healthlog.entity.HealthLog;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.service.DecisionTreeService;
import com.healthlog.service.HealthLogService;
import com.healthlog.service.InformationGainService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 健康日誌 REST 控制器（Controller 分層）。
 * 端點完全對應 §7：
 *   GET    /health-logs            取得全部
 *   POST   /health-logs            新增
 *   PUT    /health-logs/{id}       修改
 *   DELETE /health-logs/{id}       刪除
 *   GET    /health-logs/risk       依決策樹計算風險（🔒 不可省略）
 *   GET    /health-logs/analysis   資訊增益分析（§10.4 加值）
 *   GET    /health-logs/export.csv 匯出 CSV（§10.1 加值）
 */
@RestController
@RequestMapping("/health-logs")
public class HealthLogController {

    private final HealthLogService service;
    private final HealthLogRepository repository;
    private final InformationGainService infoGain;
    private final DecisionTreeService decisionTree;

    public HealthLogController(HealthLogService service,
                               HealthLogRepository repository,
                               InformationGainService infoGain,
                               DecisionTreeService decisionTree) {
        this.service = service;
        this.repository = repository;
        this.infoGain = infoGain;
        this.decisionTree = decisionTree;
    }

    /** GET /health-logs/tree — 回傳決策樹目前套用的門檻（供前端畫決策樹圖，§6） */
    @GetMapping("/tree")
    public java.util.Map<String, Object> tree() {
        return java.util.Map.of(
                "t1Sleep", decisionTree.getT1Sleep(),
                "t2Steps", decisionTree.getT2Steps(),
                "t3Mood", decisionTree.getT3Mood());
    }

    /** GET /health-logs — 取得所有紀錄（可選日期區間 from/to，§10.1） */
    @GetMapping
    public List<HealthLogDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<HealthLog> data = (from != null && to != null)
                ? service.findByRange(from, to)
                : service.findAll();
        return data.stream().map(HealthLogDto::from).toList();
    }

    /** POST /health-logs — 新增一筆，回傳含 risk_level 的結果 */
    @PostMapping
    public ResponseEntity<HealthLogDto> create(@Valid @RequestBody HealthLogRequest req) {
        HealthLog saved = service.create(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(HealthLogDto.from(saved));
    }

    /** PUT /health-logs/{id} — 修改指定日誌 */
    @PutMapping("/{id}")
    public HealthLogDto update(@PathVariable Long id, @Valid @RequestBody HealthLogRequest req) {
        return HealthLogDto.from(service.update(id, req));
    }

    /** DELETE /health-logs/{id} — 刪除指定日誌 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /health-logs/risk — 依決策樹邏輯計算並回傳目前風險等級。
     * 不帶 id：計算最新一筆；帶 id：計算指定紀錄。
     * 回傳含 reasoning 與 decisionPath（決策路徑視覺化用）。
     */
    @GetMapping("/risk")
    public RiskResult risk(@RequestParam(required = false) Long id) {
        return service.evaluateRisk(id);
    }

    /**
     * GET /health-logs/analysis — 資訊增益獨立分析端點（§10.4）。
     * 以目前 DB 中各筆的 risk_level 為標籤，重新計算三特徵的資訊增益與最佳門檻，
     * 展示「門檻怎麼算出來」而非寫死。
     */
    @GetMapping("/analysis")
    public AnalysisResult analysis() {
        List<InformationGainService.Sample> samples = service.findAll().stream()
                .filter(e -> e.getRiskLevel() != null)
                .map(e -> new InformationGainService.Sample(
                        e.getSleepHours(), e.getSteps(), e.getMoodScore(), e.getRiskLevel()))
                .toList();
        if (samples.isEmpty()) {
            throw new IllegalStateException("尚無已分類資料可供資訊增益分析");
        }
        return infoGain.analyze(samples, "資料庫中決策樹判定的 risk_level（低/中/高）");
    }
}
