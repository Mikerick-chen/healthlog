package com.healthlog.service;

import com.healthlog.dto.HealthLogRequest;
import com.healthlog.dto.RiskResult;
import com.healthlog.entity.HealthLog;
import com.healthlog.repository.HealthLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 健康日誌業務服務（Service 分層）。
 * 串接 Repository 與決策樹：新增/修改時即時計算 risk_level 並寫回（持久化為自由發揮，§5）。
 */
@Service
public class HealthLogService {

    private final HealthLogRepository repository;
    private final DecisionTreeService decisionTree;

    public HealthLogService(HealthLogRepository repository, DecisionTreeService decisionTree) {
        this.repository = repository;
        this.decisionTree = decisionTree;
    }

    /** 取得全部（新→舊） */
    public List<HealthLog> findAll() {
        return repository.findAllByOrderByLogDateDesc();
    }

    /** 依日期區間查詢（§10.1） */
    public List<HealthLog> findByRange(LocalDate start, LocalDate end) {
        return repository.findByLogDateBetweenOrderByLogDateAsc(start, end);
    }

    public HealthLog findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的紀錄"));
    }

    /** 新增：寫入後即時用決策樹算 risk_level 並持久化 */
    public HealthLog create(HealthLogRequest req) {
        LocalDate date = req.getLogDate() != null ? req.getLogDate() : LocalDate.now();
        HealthLog entity = new HealthLog(date, req.getSleepHours(), req.getSteps(), req.getMoodScore());
        // 先存一次取得 id，再計算風險回寫
        entity = repository.save(entity);
        RiskResult r = decisionTree.classify(entity);
        entity.setRiskLevel(r.getRiskLevel());
        return repository.save(entity);
    }

    /** 修改：更新三特徵後重新計算 risk_level */
    public HealthLog update(Long id, HealthLogRequest req) {
        HealthLog entity = findById(id);
        if (req.getLogDate() != null) entity.setLogDate(req.getLogDate());
        entity.setSleepHours(req.getSleepHours());
        entity.setSteps(req.getSteps());
        entity.setMoodScore(req.getMoodScore());
        RiskResult r = decisionTree.classify(entity);
        entity.setRiskLevel(r.getRiskLevel());
        return repository.save(entity);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("找不到 id=" + id + " 的紀錄");
        }
        repository.deleteById(id);
    }

    /**
     * 依決策樹計算指定紀錄（或最新一筆）的風險，回傳含判斷依據與決策路徑。
     * 對應 §7 GET /health-logs/risk。
     */
    public RiskResult evaluateRisk(Long id) {
        HealthLog entity = (id != null) ? findById(id)
                : repository.findFirstByOrderByLogDateDescIdDesc();
        if (entity == null) {
            throw new IllegalStateException("尚無任何健康日誌可供風險計算");
        }
        return decisionTree.classify(entity);
    }
}
