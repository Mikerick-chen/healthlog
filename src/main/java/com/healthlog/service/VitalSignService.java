package com.healthlog.service;

import com.healthlog.dto.VitalSignRequest;
import com.healthlog.entity.VitalSign;
import com.healthlog.repository.VitalSignRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 生命徵象業務服務（血壓/血糖/心率/體溫） */
@Service
public class VitalSignService {

    private final VitalSignRepository repo;

    public VitalSignService(VitalSignRepository repo) {
        this.repo = repo;
    }

    public List<VitalSign> findAll() { return repo.findAllByOrderByRecordDateDesc(); }

    public List<VitalSign> findByRange(LocalDate start, LocalDate end) {
        return repo.findByRecordDateBetweenOrderByRecordDateAsc(start, end);
    }

    public VitalSign create(VitalSignRequest req) {
        VitalSign e = new VitalSign();
        apply(e, req);
        return repo.save(e);
    }

    public VitalSign update(Long id, VitalSignRequest req) {
        VitalSign e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的生命徵象"));
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("找不到 id=" + id + " 的生命徵象");
        repo.deleteById(id);
    }

    private void apply(VitalSign e, VitalSignRequest req) {
        e.setRecordDate(req.getRecordDate() != null ? req.getRecordDate() : LocalDate.now());
        e.setSystolic(req.getSystolic());
        e.setDiastolic(req.getDiastolic());
        e.setHeartRate(req.getHeartRate());
        e.setBodyTemp(req.getBodyTemp());
        e.setBloodSugar(req.getBloodSugar());
        e.setMeasureContext(req.getMeasureContext());
    }
}
