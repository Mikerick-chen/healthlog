package com.healthlog.service;

import com.healthlog.dto.BodyMetricRequest;
import com.healthlog.entity.BodyMetric;
import com.healthlog.repository.BodyMetricRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 身體數據業務服務（依使用者隔離） */
@Service
public class BodyMetricService {

    private final BodyMetricRepository repo;

    public BodyMetricService(BodyMetricRepository repo) {
        this.repo = repo;
    }

    public List<BodyMetric> findAll() { return repo.findByUserIdOrderByRecordDateDesc(CurrentUser.id()); }

    public List<BodyMetric> findByRange(LocalDate start, LocalDate end) {
        return repo.findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(CurrentUser.id(), start, end);
    }

    public BodyMetric create(BodyMetricRequest req) {
        BodyMetric e = new BodyMetric();
        e.setUserId(CurrentUser.id());
        apply(e, req);
        return repo.save(e);
    }

    public BodyMetric update(Long id, BodyMetricRequest req) {
        BodyMetric e = get(id);
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) { repo.delete(get(id)); }

    private BodyMetric get(Long id) {
        BodyMetric e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的身體數據"));
        if (e.getUserId() != null && !e.getUserId().equals(CurrentUser.id()))
            throw new IllegalArgumentException("無權存取此筆資料");
        return e;
    }

    private void apply(BodyMetric e, BodyMetricRequest req) {
        e.setRecordDate(req.getRecordDate() != null ? req.getRecordDate() : LocalDate.now());
        e.setHeightCm(req.getHeightCm());
        e.setWeightKg(req.getWeightKg());
        e.setWaterMl(req.getWaterMl());
    }
}
