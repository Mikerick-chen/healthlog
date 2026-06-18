package com.healthlog.service;

import com.healthlog.dto.BodyMetricRequest;
import com.healthlog.entity.BodyMetric;
import com.healthlog.repository.BodyMetricRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 身體數據業務服務 */
@Service
public class BodyMetricService {

    private final BodyMetricRepository repo;

    public BodyMetricService(BodyMetricRepository repo) {
        this.repo = repo;
    }

    public List<BodyMetric> findAll() { return repo.findAllByOrderByRecordDateDesc(); }

    public List<BodyMetric> findByRange(LocalDate start, LocalDate end) {
        return repo.findByRecordDateBetweenOrderByRecordDateAsc(start, end);
    }

    public BodyMetric create(BodyMetricRequest req) {
        BodyMetric e = new BodyMetric();
        apply(e, req);
        return repo.save(e);
    }

    public BodyMetric update(Long id, BodyMetricRequest req) {
        BodyMetric e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的身體數據"));
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("找不到 id=" + id + " 的身體數據");
        repo.deleteById(id);
    }

    private void apply(BodyMetric e, BodyMetricRequest req) {
        e.setRecordDate(req.getRecordDate() != null ? req.getRecordDate() : LocalDate.now());
        e.setHeightCm(req.getHeightCm());
        e.setWeightKg(req.getWeightKg());
        e.setWaterMl(req.getWaterMl());
    }
}
