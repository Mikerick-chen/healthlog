package com.healthlog.service;

import com.healthlog.dto.HealthLogRequest;
import com.healthlog.dto.RiskResult;
import com.healthlog.entity.HealthLog;
import com.healthlog.repository.HealthLogRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 健康日誌業務服務（Service 分層，已依目前使用者隔離資料，§9）。
 * 新增/修改時即時用決策樹計算 risk_level 並寫回（持久化為自由發揮，§5）。
 */
@Service
public class HealthLogService {

    private final HealthLogRepository repository;
    private final DecisionTreeService decisionTree;

    public HealthLogService(HealthLogRepository repository, DecisionTreeService decisionTree) {
        this.repository = repository;
        this.decisionTree = decisionTree;
    }

    public List<HealthLog> findAll() {
        return repository.findByUserIdOrderByLogDateDesc(CurrentUser.id());
    }

    public List<HealthLog> findByRange(LocalDate start, LocalDate end) {
        return repository.findByUserIdAndLogDateBetweenOrderByLogDateAsc(CurrentUser.id(), start, end);
    }

    public HealthLog findById(Long id) {
        HealthLog e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的紀錄"));
        ensureOwner(e.getUserId());
        return e;
    }

    public HealthLog create(HealthLogRequest req) {
        LocalDate date = req.getLogDate() != null ? req.getLogDate() : LocalDate.now();
        HealthLog entity = new HealthLog(date, req.getSleepHours(), req.getSteps(), req.getMoodScore());
        entity.setUserId(CurrentUser.id());
        entity = repository.save(entity);
        RiskResult r = decisionTree.classify(entity);
        entity.setRiskLevel(r.getRiskLevel());
        return repository.save(entity);
    }

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
        HealthLog e = findById(id); // 含擁有者檢查
        repository.delete(e);
    }

    /** 依決策樹計算指定紀錄（或目前使用者最新一筆）的風險 */
    public RiskResult evaluateRisk(Long id) {
        HealthLog entity = (id != null) ? findById(id)
                : repository.findFirstByUserIdOrderByLogDateDescIdDesc(CurrentUser.id());
        if (entity == null) {
            throw new IllegalStateException("尚無任何健康日誌可供風險計算");
        }
        return decisionTree.classify(entity);
    }

    /** 確認資料屬於目前使用者，避免越權存取 */
    private void ensureOwner(Long ownerId) {
        if (ownerId != null && !ownerId.equals(CurrentUser.id())) {
            throw new IllegalArgumentException("無權存取此筆資料");
        }
    }
}
